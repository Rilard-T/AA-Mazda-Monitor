package com.aatorque.nav

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.aatorque.stats.R
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleOwner
import com.aatorque.obd.ObdBridgeService
import com.aatorque.stats.NotiService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import timber.log.Timber

/** Вход для Android Auto в категории навигации: приборы рисуются на всей поверхности. */
class NavCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = NavSession()
}

class NavSession : Session() {
    private var conn: ServiceConnection? = null
    private var screen: NavScreen? = null

    private val mediaRegistrations = HashMap<Int, () -> Unit>()
    private val mediaUpdates = MutableSharedFlow<MediaMetadata?>(extraBufferCapacity = 1)
    private var mediaJob: Job? = null

    override fun onCreateScreen(intent: Intent): Screen {
        // запускаем и держим службу связи с адаптером, пока открыт экран
        ObdBridgeService.start(carContext)
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        conn = c
        try {
            carContext.bindService(Intent(carContext, ObdBridgeService::class.java), c, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            conn = null
        }
        NavData.start()
        startMediaTracking()

        carContext.getCarService(AppManager::class.java).setSurfaceCallback(NavRenderer(carContext))

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            // после выхода в меню магнитолы и возврата хост отдаёт прежнюю (маленькую) область,
            // пока не перезапросить шаблон: обновляем его при каждом показе экрана
            override fun onStart(owner: LifecycleOwner) {
                screen?.invalidate()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                stopMediaTracking()
                NavData.stop()
                conn?.let {
                    try {
                        carContext.unbindService(it)
                    } catch (e: Exception) {
                    }
                }
                conn = null
            }
        })
        return NavScreen(carContext).also { screen = it }
    }

    private fun startMediaTracking() {
        if (!NotiService.isNotificationAccessEnabled(carContext)) return
        mediaJob = lifecycleScope.launch {
            mediaUpdates.asSharedFlow().distinctUntilChangedBy { meta ->
                listOf(
                    meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ).hashCode()
            }.collect { meta ->
                val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                val text = when {
                    !artist.isNullOrBlank() && !title.isNullOrBlank() -> "$artist — $title"
                    !title.isNullOrBlank() -> title
                    !artist.isNullOrBlank() -> artist
                    else -> null
                }
                NavData.setText("media_title", text)
            }
        }
        registerMediaSessions()
    }

    private fun registerMediaSessions() {
        val mm = ContextCompat.getSystemService(carContext, MediaSessionManager::class.java) ?: return
        val component = ComponentName(carContext, NotiService::class.java)
        try {
            mm.addOnActiveSessionsChangedListener({ controllers ->
                updateMediaSessions(controllers ?: emptyList())
            }, component)
            updateMediaSessions(mm.getActiveSessions(component))
        } catch (e: SecurityException) {
            Timber.e("No permission to read media", e)
        }
    }

    private fun updateMediaSessions(controllers: List<MediaController>) {
        val found = controllers.map { mc ->
            val code = mc.sessionToken.hashCode()
            if (!mediaRegistrations.containsKey(code)) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        lifecycleScope.launch { mediaUpdates.emit(metadata) }
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (isActive(state)) {
                            lifecycleScope.launch { mediaUpdates.emit(mc.metadata) }
                        }
                    }
                }
                mc.registerCallback(cb)
                mediaRegistrations[code] = {
                    mc.unregisterCallback(cb)
                    mediaRegistrations.remove(code)
                }
                val meta = mc.metadata
                if (meta != null && (!meta.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() || isActive(mc.playbackState))) {
                    lifecycleScope.launch { mediaUpdates.emit(meta) }
                }
            }
            code
        }.toSet()
        mediaRegistrations.filterNot { found.contains(it.key) }.forEach { it.value() }
    }

    private fun isActive(state: PlaybackState?): Boolean = when (state?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
        else -> false
    }

    private fun stopMediaTracking() {
        mediaJob?.cancel()
        mediaJob = null
        mediaRegistrations.values.toList().forEach { it() }
        mediaRegistrations.clear()
        NavData.setText("media_title", null)
    }
}

class NavScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val exit = Action.Builder()
            .setTitle(carContext.getString(R.string.nav_exit))
            .setOnClickListener { carContext.finishCarApp() }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(exit).build())
            .build()
    }
}
