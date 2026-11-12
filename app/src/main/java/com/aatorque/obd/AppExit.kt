package com.aatorque.obd

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.aatorque.stats.R
import kotlin.system.exitProcess

/** Подтверждение и полный выход из приложения (сервис, соединение с адаптером, процесс). */
object AppExit {
    fun confirmAndExit(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setPositiveButton(android.R.string.yes) { _, _ -> exitNow(activity) }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    fun exitNow(activity: Activity) {
        runCatching { ObdBridgeService.stop(activity) }
        activity.finishAndRemoveTask()
        // Даём сервису обработать остановку, затем завершаем процесс целиком
        Handler(Looper.getMainLooper()).postDelayed({
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }, 400)
    }

    /**
     * «Назад» на главном экране спрашивает подтверждение выхода.
     * Во вложенных экранах (стек фрагментов не пуст) работает как обычно.
     */
    fun installBackConfirm(activity: AppCompatActivity) {
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activity.supportFragmentManager.backStackEntryCount > 0) {
                    isEnabled = false
                    activity.onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                } else {
                    confirmAndExit(activity)
                }
            }
        })
    }
}
