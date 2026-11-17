package com.aatorque.stats

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import com.aatorque.obd.ObdBridgeService
import org.prowl.torque.remote.ITorqueService
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

typealias PidInfo = List<Pair<String, List<String>>>
class StatsService: Service() {
    private val binder = LocalBinder()
    private var bound = false
    var wasStartAttempted = false
    var obdService: ITorqueService? = null
    val onConnect = ArrayList<(ITorqueService) -> Unit>()
    var pids: PidInfo? = null
    var connectCount = 0
    val conLock = ReentrantLock()

    override fun onCreate() {
        super.onCreate()
        if (!wasStartAttempted) {
            wasStartAttempted = connect()
        }
        connectCount++
    }

    override fun onDestroy() {
        super.onDestroy()
        connectCount--
        // unbindService бросает IllegalArgumentException, если привязки не было
        // (connect вернул false), поэтому проверяем флаг и глушим исключение.
        if (connectCount == 0 && bound) {
            runCatching { unbindService(serviceConnection) }
            bound = false
        }
    }

    class ListPids(val ts: ITorqueService, val onComplete: (PidInfo) -> Unit): Runnable {
        override fun run() {
            try {
                val pids = ts.listAllPIDs()
                val pidAttributes = ts.getPIDInformation(pids).map { it.split(",") }

                onComplete(
                    pids.zip(pidAttributes).sortedBy { it.second[0] }
                )
            } catch (t: Throwable) {
                // Раньше сбой в этом потоке терялся молча: список PID просто оставался пустым
                Timber.e(t, "Не удалось получить список PID от службы OBD")
            }
        }

    }

    fun loadPidInformation(force: Boolean = false, onComplete: ((PidInfo) -> Unit)? = null) {
        if (pids != null && !force) {
            onComplete?.invoke(pids!!)
            return
        }
        addConnectCallback {
            val bg = ListPids(it) {
                newPids ->
                this.pids = newPids
                onComplete?.invoke(newPids)
            }
            Thread(bg).start()
        }
    }
    /**
     * Class used for the client Binder. Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods.
        fun getService(): StatsService = this@StatsService
    }

    fun isAvailable(): Boolean {
        return obdService != null
    }

    fun addConnectCallback(func: (ITorqueService) -> Unit): StatsService {
        if (obdService == null) {
            conLock.withLock {
                onConnect.add(func)
            }
        } else {
            func(obdService!!)
        }
        return this
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }


    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        /** Что делать, когда служба OBD подключилась. */
        override fun onServiceConnected(arg0: ComponentName, service: IBinder) {
            val svc = ITorqueService.Stub.asInterface(service)
            conLock.withLock {
                for (funt in onConnect) {
                    funt(svc)
                }
                onConnect.clear()
            }
            obdService = svc
        }

        /** Что делать при отключении службы OBD. */
        override fun onServiceDisconnected(name: ComponentName) {
            obdService = null
        }
    }

    fun connect(): Boolean {
        val intent = Intent(this, ObdBridgeService::class.java)
        ObdBridgeService.start(this)
        val boundService = bindService(intent, serviceConnection, Activity.BIND_AUTO_CREATE)
        bound = boundService
        Timber.i(
            if (boundService) "Connected to OBD service!" else "Unable to connect to OBD service"
        )
        return boundService
    }

    companion object {
        fun runStartIntent(context: Context, conn: ServiceConnection): Boolean {
            Intent(context, StatsService::class.java).also { intent ->
                return context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            }
        }
    }
}
