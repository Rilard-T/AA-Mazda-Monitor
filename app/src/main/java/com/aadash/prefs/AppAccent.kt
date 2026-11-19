package com.aadash.prefs

import android.app.Activity
import android.content.Context
import androidx.annotation.StyleRes
import androidx.preference.PreferenceManager
import com.aadash.stats.R

/**
 * Акцентный цвет интерфейса настроек.
 *
 * Значение хранится в общих настройках под ключом [KEY]. Экраны используют тему
 * `Theme.App.Settings`, поверх которой накладывается `ThemeOverlay.App.Accent.*`: в overlay задана
 * вся пара семейств Material 3 (primary и secondary с контейнерами), поэтому цвет получают
 * переключатели, радиокнопки, кнопки диалогов, ползунки, чипы и подсветка нажатий, а не только
 * подписи с `?attr/colorAccent`.
 */
object AppAccent {
    const val KEY = "app_accent_color"
    const val DEFAULT = "blue"

    /**
     * Читаем именно файл настроек PreferenceFragment: `getSharedPreferences(null, …)` открывает
     * отдельный `null.xml`, а ListPreference пишет в `<пакет>_preferences`, поэтому выбор цвета
     * сохранялся, но интерфейс продолжал брать значение по умолчанию (синий).
     */
    fun value(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(KEY, DEFAULT) ?: DEFAULT

    @StyleRes
    fun overlayFor(value: String): Int = when (value) {
        "green" -> R.style.ThemeOverlay_App_Accent_Green
        "red" -> R.style.ThemeOverlay_App_Accent_Red
        "orange" -> R.style.ThemeOverlay_App_Accent_Orange
        "purple" -> R.style.ThemeOverlay_App_Accent_Purple
        else -> R.style.ThemeOverlay_App_Accent_Blue
    }

    /**
     * Накладывает акцент на тему экрана. Вызывать до `super.onCreate()`: иначе верхняя панель и
     * элементы, созданные вместе с окном, остаются в прежнем цвете.
     *
     * DynamicColors (Material You) намеренно не вызывается: на Android 12+ он перекрашивал
     * поверхности под обои, и экран выглядел по-разному на разных устройствах. Палитра экранов
     * настроек задана полностью в `Theme.App.Settings`, поэтому акцент — единственная
     * переменная часть оформления.
     */
    fun applyTo(activity: Activity) {
        activity.theme.applyStyle(overlayFor(value(activity)), true)
    }
}
