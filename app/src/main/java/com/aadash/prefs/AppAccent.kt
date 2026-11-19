package com.aadash.prefs

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.aadash.stats.R
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import kotlin.math.abs

/**
 * Акцентный цвет интерфейса настроек.
 *
 * Хранится один цвет-затравка (seed) в общем файле настроек под ключом [KEY]. Из него Material 3
 * строит полную палитру: primary/secondary/tertiary со своими контейнерами, инверсными тонами и
 * подсветкой нажатий, поэтому цвет получают переключатели, радиокнопки, кнопки диалогов,
 * ползунки, чипы и ripple, а не только подписи.
 *
 * Палитру строит сам Material (`DynamicColors` с content-based seed) — это работает на Android 12
 * и новее. На более старых версиях ResourcesLoader недоступен, поэтому берётся ближайший по
 * оттенку встроенный пресет: цвет может отличаться от выбранного, но акцент всё равно применяется.
 */
object AppAccent {
    /** Числовой seed-цвет в файле настроек главного экрана. */
    const val KEY = "app_accent_seed"

    /** Ключ старого списка пресетов (строковое имя). Нужен только для миграции. */
    const val LEGACY_KEY = "app_accent_color"

    /** Пресет по умолчанию — синий, совпадает с базовой палитрой ms_*. */
    const val DEFAULT_PRESET = "blue"

    private class Preset(val name: String, @ColorInt val color: Int, @StyleRes val overlay: Int)

    private fun presets(context: Context): List<Preset> = listOf(
        Preset("blue", color(context, R.color.ms_primary), R.style.ThemeOverlay_App_Accent_Blue),
        Preset("green", color(context, R.color.accent_green_primary), R.style.ThemeOverlay_App_Accent_Green),
        Preset("red", color(context, R.color.accent_red_primary), R.style.ThemeOverlay_App_Accent_Red),
        Preset("orange", color(context, R.color.accent_orange_primary), R.style.ThemeOverlay_App_Accent_Orange),
        Preset("purple", color(context, R.color.accent_purple_primary), R.style.ThemeOverlay_App_Accent_Purple)
    )

    private fun color(context: Context, id: Int): Int = ContextCompat.getColor(context, id)

    /** Текущий цвет акцента. Старое строковое значение пресета тоже понимаем. */
    @ColorInt
    fun seed(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val stored = prefs.all[KEY]
        if (stored is Int) return stored
        return seedForPreset(context, prefs.all[LEGACY_KEY] as? String)
    }

    fun save(context: Context, @ColorInt seed: Int) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putInt(KEY, seed)
            .remove(LEGACY_KEY)
            .apply()
    }

    /** Цвет встроенного пресета по его прежнему строковому имени; неизвестное имя — синий. */
    @ColorInt
    fun seedForPreset(context: Context, name: String?): Int {
        val list = presets(context)
        return list.firstOrNull { it.name == name }?.color ?: list.first().color
    }

    /**
     * Накладывает акцент на тему экрана. Вызывать до `super.onCreate()`: иначе верхняя панель и
     * элементы, созданные вместе с окном, остаются в прежнем цвете.
     */
    fun applyTo(activity: Activity) {
        val seed = seed(activity)
        if (applyDynamic(activity, seed)) return
        activity.theme.applyStyle(presetOverlay(activity, seed), true)
    }

    /**
     * Полная палитра Material 3 из одного цвета. Возвращает false, если система не умеет
     * подменять цвета ресурсов (до Android 12) — тогда экран берёт ближайший пресет.
     */
    private fun applyDynamic(activity: Activity, @ColorInt seed: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        var applied = false
        DynamicColors.applyToActivityIfAvailable(
            activity,
            DynamicColorsOptions.Builder()
                .setContentBasedSource(seed)
                .setPrecondition { _, _ -> true }
                .setOnAppliedCallback { applied = true }
                .build()
        )
        if (applied) {
            // Палитра из цвета перекрашивает и поверхности. Возвращаем нейтральные, чтобы экраны
            // настроек не уезжали в оттенок акцента: цветными остаются только акцентные роли.
            activity.theme.applyStyle(R.style.ThemeOverlay_App_Surfaces, false)
        }
        return applied
    }

    @StyleRes
    private fun presetOverlay(context: Context, @ColorInt seed: Int): Int {
        val list = presets(context)
        return (list.minByOrNull { hueDistance(it.color, seed) } ?: list.first()).overlay
    }

    /** Ближайший оттенок по кругу; для серых цветов сравниваем светлоту, а не оттенок. */
    private fun hueDistance(a: Int, b: Int): Float {
        val hsvA = FloatArray(3)
        val hsvB = FloatArray(3)
        Color.colorToHSV(a, hsvA)
        Color.colorToHSV(b, hsvB)
        if (hsvA[1] < 0.08f || hsvB[1] < 0.08f) return abs(hsvA[2] - hsvB[2]) * 360f
        val diff = abs(hsvA[0] - hsvB[0]) % 360f
        return if (diff > 180f) 360f - diff else diff
    }
}
