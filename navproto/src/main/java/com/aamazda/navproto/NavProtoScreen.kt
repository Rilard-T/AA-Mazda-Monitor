package com.aamazda.navproto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate

class NavProtoScreen(carContext: CarContext) : Screen(carContext) {
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
