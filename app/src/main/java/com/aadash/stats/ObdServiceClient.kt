package com.aadash.stats

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import com.aadash.obd.ObdBridgeService
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
        val svc = conLock.withLock {
            val current = obdService
            if (current == null) {
                onConnect.add(func)
            }
            current
        }
        if (svc != null) {
            func(svc)
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
                val callbacks = conLock.withLock {
                    obdService = svc
                    val copy = onConnect.toList()
                    onConnect.clear()
                    copy
                }
                for (funt in callbacks) {
                    funt(svc)
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