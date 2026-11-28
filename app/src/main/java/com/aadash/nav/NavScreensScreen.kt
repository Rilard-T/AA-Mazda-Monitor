package com.aadash.nav

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class NavScreensScreen(carContext: CarContext, private val navScreen: NavScreen) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        val count = navScreen.screenCount()
        for (i in 0 until count) {
            val name = if (i == 0) "Приборы" else NavLayoutStore.layout.graphs.getOrNull(i - 1)?.name ?: "График $i"
            val row = Row.Builder()
                .setTitle(name)
                .setOnClickListener {
                    NavScreen.currentIndex = i
                    navScreen.invalidate()
                    screenManager.pop()
                }
                .build()
            listBuilder.addItem(row)
        }

        val back = Action.Builder()
            .setTitle("Назад")
            .setOnClickListener { screenManager.pop() }
            .build()

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Экраны")
            .setHeaderAction(back)
            .build()
    }
}
