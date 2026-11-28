package com.aadash.stats

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class AlbumArt : CarFragment() {

    val registed = HashMap<Int, () -> Unit>()
    private val updateChannel = MutableSharedFlow<MediaMetadata?>()
    private var mediaJob: kotlinx.coroutines.Job? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var mediaManager: MediaSessionManager? = null

    override fun onResume() {
        super.onResume()
        setupListenMedia()
    }

    override fun onPause() {
        super.onPause()
        mediaJob?.cancel()
        mediaJob = null
        sessionsListener?.let { listener ->
            try {
                mediaManager?.removeOnActiveSessionsChangedListener(listener)
            } catch (_: Exception) {}
        }
        sessionsListener = null
        mediaManager = null
        updateSessions(emptyList())
    }

    private fun setupListenMedia() {
        mediaJob?.cancel()
        mediaJob = viewLifecycleOwner.lifecycleScope.launch {
            updateChannel.asSharedFlow()
                .distinctUntilChangedBy {
                    listOf(
                        metaDataToArt(it)?.hashCode(),
                        it?.getString(MediaMetadata.METADATA_KEY_TITLE),
                        it?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ).hashCode()
                }
                .collect {
                    Timber.i("Sending new metadata ${it}")
                    onMediaChanged(it)
                }
        }
        registerMedia()
    }

    private fun registerMedia() {
        val ctx = context ?: return
        val mediaMan = ContextCompat.getSystemService(
            ctx,
            MediaSessionManager::class.java
        ) ?: return
        val component = ComponentName(ctx, NotiService::class.java)
        sessionsListener?.let { prev ->
            try { mediaManager?.removeOnActiveSessionsChangedListener(prev) } catch (_: Exception) {}
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener {
            updateSessions(it ?: emptyList())
        }
        try {
            mediaMan.addOnActiveSessionsChangedListener(listener, component)
            mediaManager = mediaMan
            sessionsListener = listener
            updateSessions(mediaMan.getActiveSessions(component))
        } catch (e: SecurityException) {
            Timber.e("No permission to read media", e)
        }
    }

    private fun updateSessions(mediaControllers: List<MediaController>) {
        val found = mediaControllers.map {
            val token = it.sessionToken
            val code = token.hashCode()
            if (!registed.containsKey(code)) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        super.onMetadataChanged(metadata)
                        lifecycleScope.launch {
                            updateChannel.emit(metadata)
                        }
                    }
                }
                it.registerCallback(callback)
                registed[code] = {
                    it.unregisterCallback(callback)
                    registed.remove(code)
                }
                if (isActive(it.playbackState)) {
                    lifecycleScope.launch {
                        updateChannel.emit(it.metadata)
                        Timber.i("Sending initial metadata ${it.metadata}")
                    }
                }
            }
            code
        }.toSet()
        registed.filterNot { found.contains(it.key) }.forEach {
            it.value()
        }
    }

    open fun isActive(state: PlaybackState?): Boolean {
        return when (state?.state) {
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_PLAYING -> true

            else -> false
        }
    }

    fun metaDataToArt(medadata: MediaMetadata?): Bitmap? {
        return medadata?.getBitmap(
            MediaMetadata.METADATA_KEY_ART
        ) ?: medadata?.getBitmap(
            MediaMetadata.METADATA_KEY_ALBUM_ART
        )
    }

    abstract suspend fun onMediaChanged(medadata: MediaMetadata?)
}