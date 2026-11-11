package com.aatorque.nav

import android.content.ComponentName
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.aatorque.obd.ObdBridgeService

/** Вход для Android Auto в категории навигации: приборы рисуются на всей поверхности. */
class NavCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = NavSession()
}

class NavSession : Session() {
    private var conn: ServiceConnection? = null

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

        carContext.getCarService(AppManager::class.java).setSurfaceCallback(NavRenderer())

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
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
        return NavScreen(carContext)
    }
}

class NavScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val exit = Action.Builder()
            .setTitle("Выход")
            .setOnClickListener { carContext.finishCarApp() }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(exit).build())
            .build()
    }
}
