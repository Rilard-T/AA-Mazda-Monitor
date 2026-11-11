package com.aamazda.navproto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class NavProtoService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = NavProtoSession()
}

class NavProtoSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val renderer = SurfaceRenderer()
        carContext.getCarService(androidx.car.app.AppManager::class.java).setSurfaceCallback(renderer)
        return NavProtoScreen(carContext)
    }
}
