package com.aamazda.navproto

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this)
        t.text = "AA Monitor Nav (прототип)\n\nЭкран на телефоне пустой по задумке. Подключи телефон к Android Auto и найди приложение в списке."
        t.textSize = 18f
        t.setPadding(48, 96, 48, 48)
        setContentView(t)
    }
}
