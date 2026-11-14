package com.aatorque.obd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.IInterface
import android.util.Log
import com.aatorque.stats.R
import org.prowl.torque.remote.ITorqueService
import timber.log.Timber

/**
 * Локальная замена Torque Pro. Отдаёт тот же интерфейс ITorqueService, но данные берёт
 * напрямую из ELM327. Форк AA Torque привязывается к этому сервису вместо Torque.
 * Работает как foreground-сервис (тип connectedDevice), чтобы система не выгружала его в фоне.
 */
class ObdBridgeService : Service() {
    private var engine: ObdEngine?
        get() = current
        set(v) { current = v }
    @Volatile private var running = false
    @Volatile private var isForeground = false
    private var worker: Thread? = null
    private val prefs by lazy { getSharedPreferences("obd_bridge", MODE_PRIVATE) }

    /**
     * Binder, у которого queryLocalInterface отдаёт наш объект: ITorqueService.Stub.asInterface()
     * в форке вернёт именно его.
     */
    private val binder: IBinder = object : Binder() {
        override fun queryLocalInterface(descriptor: String): IInterface? =
            if (descriptor == DESCRIPTOR) bridge else null
    }

    /**
     * Мост к ITorqueService без рефлексии и без сверки имён методов: наследуем сгенерированный
     * AIDL-класс Default и переопределяем только то, что отдаём сами, а остальные методы
     * получают безопасные значения из Default. Переименование методов обфускатором такому
     * мосту не мешает: вызовы разрешаются компилятором, а не строками.
     */
    private val bridge: ITorqueService = object : ITorqueService.Default() {
        override fun asBinder(): IBinder = binder

        override fun listAllPIDs(): Array<String> = guarded(
            "listAllPIDs",
            { MazdaPids.all.map { it.id }.toTypedArray() }
        ) {
            engine?.listIds() ?: MazdaPids.all.map { it.id }.toTypedArray()
        }

        override fun getPIDInformation(pidIDs: Array<String>): Array<String> = guarded(
            "getPIDInformation",
            { Array(pidIDs.size) { "" } }
        ) {
            Array(pidIDs.size) { MazdaPids.byId(pidIDs[it])?.info(this@ObdBridgeService) ?: "" }
        }

        override fun getPIDValuesAsDouble(pidsToRetrieve: Array<String>): DoubleArray = guarded(
            "getPIDValuesAsDouble",
            { DoubleArray(pidsToRetrieve.size) }
        ) {
            val e = engine
            DoubleArray(pidsToRetrieve.size) { e?.value(pidsToRetrieve[it]) ?: 0.0 }
        }

        override fun isConnectedToECU(): Boolean =
            guarded("isConnectedToECU", { false }) { engine != null }
    }

    /** Вызов моста: сбой пишется в журнал, вызывающий получает безопасное значение вместо исключения. */
    private inline fun <T> guarded(what: String, fallback: () -> T, block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        Timber.e(t, "Мост ITorqueService: сбой в %s", what)
        fallback()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        running = true
        goForeground(statusText(this))    // для сервиса, запущенного только привязкой, может быть запрещено — это нормально
        worker = Thread(::connectLoop, "obd-connect").also { it.isDaemon = true; it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isForeground && !goForeground(statusText(this))) {
            // startForegroundService() обязывает вызвать startForeground(); если не вышло — не держим сервис
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        engine?.close(); engine = null
        status = getString(R.string.obd_status_stopped)
        super.onDestroy()
    }

    // ---------------- foreground и уведомление ----------------

    private fun goForeground(text: String): Boolean {
        val n = buildNotification(text)
        return try {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(NOTIF_ID, n)
            isForeground = true
            true
        } catch (e: Exception) {   // Android 12+: старт из фона запрещён; либо нет разрешения/типа в манифесте
            Log.w(TAG, "foreground не запущен: ${e.message}")
            isForeground = false
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.obd_notif_channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, ObdSettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ObdBridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.obd_notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.obd_notif_stop), stop)
            .setOngoing(true)
            .build()
    }

    /** Обновляет статус для настроек и текст уведомления. */
    private fun post(text: String) {
        status = text
        if (isForeground) {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        }
    }

    // ---------------- подключение ----------------

    /** Канал связи и подсказка на случай, если его не удалось открыть. */
    private class Target(val link: ObdLink, val label: String, val hint: String)

    /** Создаёт канал по выбранному типу подключения; если не получилось, причина остаётся в статусе. */
    @SuppressLint("MissingPermission")
    private fun createTarget(): Target? {
        when (prefs.getString("link_type", "classic")) {
            "wifi" -> {
                val host = prefs.getString("wifi_host", null)?.trim().orEmpty().ifBlank { "192.168.0.10" }
                val port = prefs.getString("wifi_port", null)?.trim()?.toIntOrNull() ?: 35000
                return Target(
                    WifiLink(applicationContext, host, port), "Wi-Fi $host:$port",
                    getString(R.string.obd_hint_wifi)
                )
            }
            "ble" -> {
                if (Build.VERSION.SDK_INT >= 31 &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    post(getString(R.string.obd_err_no_bt_permission))
                    return null
                }
                val mac = prefs.getString("ble_mac", null)?.takeIf { it.isNotBlank() }
                if (mac == null) {
                    post(getString(R.string.obd_err_choose_ble))
                    return null
                }
                val dev = BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(mac) ?: return null
                return Target(
                    BleLink(applicationContext, dev), "BLE ${dev.name ?: mac}",
                    getString(R.string.obd_hint_ble)
                )
            }
            else -> {
                val dev = findDevice()
                if (dev == null) {
                    // не затираем сообщение о недостающем разрешении
                    val noPermission = getString(R.string.obd_err_no_bt_permission)
                    post(if (status == noPermission) noPermission else getString(R.string.obd_err_adapter_not_found))
                    return null
                }
                return Target(
                    Elm327Link(applicationContext, dev), dev.name ?: dev.address,
                    getString(R.string.obd_hint_classic)
                )
            }
        }
    }

    private fun connectLoop() {
        while (running) {
            var link: ObdLink? = null      // закрываем при любом сбое, иначе адаптер остаётся занятым нами же
            try {
                reconnectFlag = false
                val t = createTarget()
                if (t == null) {
                    Thread.sleep(5000); continue
                }
                post(getString(R.string.obd_status_connecting, t.label))
                link = t.link
                try {
                    t.link.open()
                } catch (ex: java.io.IOException) {
                    throw java.io.IOException(t.hint + " " + ex.message)
                }
                post(getString(R.string.obd_status_initializing))
                val e = ObdEngine(t.link, prefs, applicationContext)
                e.init()
                engine = e
                post(getString(R.string.obd_status_connected))
                while (running && e.failures < 5 && !reconnectFlag) Thread.sleep(1000)
                post(if (reconnectFlag) getString(R.string.obd_status_reconnecting) else getString(R.string.obd_status_lost))
                reconnectFlag = false
                engine = null
                e.close()
            } catch (ie: InterruptedException) {
                link?.close()
                break
            } catch (ex: Exception) {
                engine?.close(); engine = null
                link?.close()
                post(ex.message ?: getString(R.string.obd_err_generic))
                try { Thread.sleep(3000) } catch (_: InterruptedException) { break }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun findDevice(): BluetoothDevice? {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status = getString(R.string.obd_err_no_bt_permission)
            return null
        }
        val bonded = BluetoothAdapter.getDefaultAdapter()?.bondedDevices ?: return null
        val mac = prefs.getString("obd_mac", null)?.takeIf { it.isNotBlank() } ?: ObdConfig.ADAPTER_MAC.takeIf { it.isNotBlank() }
        if (mac != null) bonded.firstOrNull { it.address.equals(mac, ignoreCase = true) }?.let { return it }
        val re = Regex("obd|elm|v-?link|vgate|konnwei|icar|veepeak|scan|327", RegexOption.IGNORE_CASE)
        return bonded.firstOrNull { re.containsMatchIn(it.name ?: "") }
    }

    companion object {
        private const val TAG = "ObdBridge"
        private const val DESCRIPTOR = "org.prowl.torque.remote.ITorqueService"
        private const val CHANNEL = "obd_bridge"
        private const val NOTIF_ID = 4711
        private const val ACTION_STOP = "com.aatorque.obd.STOP"

        /** Текст состояния для экрана настроек; null — служба ещё не сообщала состояние. */
        @Volatile var status: String? = null

        @Volatile private var current: ObdEngine? = null
        @Volatile private var reconnectFlag = false

        /** Текст состояния с учётом языка телефона. */
        fun statusText(context: Context): String =
            status ?: context.getString(R.string.obd_status_not_started)

        /** Текущее значение параметра для экрана проверки. Вызывать только не из главного потока. */
        fun peek(id: String): Double? = current?.value(id)

        /** Переподключиться (например, после смены адаптера). */
        fun reconnect(context: Context) {
            reconnectFlag = true
            start(context)
        }

        /** Остановить foreground-режим и сервис. */
        fun stop(context: Context) {
            try {
                context.startService(Intent(context, ObdBridgeService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
                Log.w(TAG, "stop: ${e.message}")
            }
        }

        /**
         * Запускает сервис как foreground. Безопасно вызывать откуда угодно: если система запрещает
         * старт из фона (Android 12+), исключение гасится, и сервис просто работает как привязанный.
         * Гарантированно разрешено, когда приложение на экране (например, из SettingsActivity).
         */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, ObdBridgeService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "start: ${e.message}")
            }
        }
    }
}
