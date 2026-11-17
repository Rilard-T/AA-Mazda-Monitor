package com.aatorque.stats

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import com.aatorque.obd.ObdBridgeService
import org.prowl.torque.remote.ITorqueService
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ObdServiceClient {
    var obdService: ITorqueService? = null
    val onConnect = ArrayList<(ITorqueService) -> Unit>()
    val conLock = ReentrantLock()
    var hasBound = false

    fun addConnectCallback(func: (ITorqueService) -> Unit): ObdServiceClient {
        if (obdService == null) {
            conLock.withLock {
                onConnect.add(func)
            }
        } else {
            func(obdService!!)
        }
        return this
    }

    fun runIfConnected(func: (ITorqueService) -> Unit) {
        if (obdService != null) {
            func(obdService!!)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        var forceReconnect: (()-> Unit)? = null

        /** Что делать, когда служба OBD подключилась. */
        override fun onServiceConnected(arg0: ComponentName, service: IBinder) {
            try {
                val svc = ITorqueService.Stub.asInterface(service)
                if (BuildConfig.SIMULATE_METRICS) {
                    svc.setDebugTestMode(!svc.isConnectedToECU)
                }
                obdService = svc
                conLock.withLock {
                    for (funt in onConnect) {
                        funt(svc)
                    }
                    onConnect.clear()
                }
            } catch (e: DeadObjectException) {
                Timber.e("Disconnected from service", e)
                obdService = null
                forceReconnect?.invoke()
            }
        }

        /** Что делать при отключении службы OBD. */
        override fun onServiceDisconnected(name: ComponentName) {
            obdService = null
        }
    }

    fun onDestroy(context: Context) {
        // Снимаем привязку по флагу, а не по наличию соединения: если bindService() уже
        // вызван, но onServiceConnected ещё не пришёл, прежняя проверка оставляла
        // BIND_AUTO_CREATE висеть, и служба не останавливалась вместе с приложением.
        if (hasBound) {
            runCatching { context.unbindService(serviceConnection) }
            hasBound = false
        }
        obdService = null
    }

    fun connect(context: Context): Boolean {
        val intent = Intent(context, ObdBridgeService::class.java)
        ObdBridgeService.start(context)
        hasBound = context.bindService(intent, serviceConnection, Activity.BIND_AUTO_CREATE)
        serviceConnection.forceReconnect = {
            onDestroy(context)
            context.bindService(intent, serviceConnection, Activity.BIND_AUTO_CREATE)
        }
        Timber.i(
            if (hasBound) "Connected to OBD service!" else "Unable to connect to OBD service"
        )
        return hasBound
    }

}