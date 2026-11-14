package com.aatorque.nav

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import com.aatorque.stats.R

/**
 * Ресурсы темы прибора из старого интерфейса: картинки циферблата (с делениями и пустой), стрелки, фона,
 * цвет стрелки и цвет индикатора. Загружаются по названию темы из настроек («Mazda gen4» и т.д.).
 */
class NavThemeRes(
    val name: String,
    val dialTicks: Drawable?,
    val dialEmpty: Drawable?,
    val needle: Drawable?,
    val needleColor: Int,
    val indicatorColor: Int,
    val indicatorWidth: Float,
    val carBackground: Drawable?
) {
    /** Как в старом интерфейсе: картинка стрелки, если цвет индикатора в теме - один из двух особых цветов. */
    val imageNeedle: Boolean
        get() = needle != null && (indicatorColor == AQUA || indicatorColor == ALT)

    companion object {
        const val AQUA = 1996533487
        const val ALT = -14575885
    }
}

object NavThemes {
    /** Названия тем в порядке списка в настройках. */
    fun names(context: Context): List<String> =
        context.applicationContext.resources.getStringArray(R.array.Themes).toList()

    /** Загрузить тему по названию; пустое или неизвестное название - null (плоский вид). */
    fun load(context: Context, name: String): NavThemeRes? {
        if (name.isEmpty()) return null
        try {
            val app = context.applicationContext
            val index = app.resources.getStringArray(R.array.Themes).indexOf(name)
            if (index < 0) return null
            val arr = app.resources.obtainTypedArray(R.array.ThemeList)
            val style = try {
                arr.getResourceId(index, 0)
            } finally {
                arr.recycle()
            }
            if (style == 0) return null
            val themed = ContextThemeWrapper(app, style)
            return NavThemeRes(
                name,
                drawable(themed, "themedDialBackground"),
                drawable(themed, "themedEmptyDialBackground"),
                drawable(themed, "themedNeedle"),
                color(themed, "themedNeedleColor", 0xFFFF3030.toInt()),
                color(themed, "sv_indicatorColor", 0),
                dimension(themed, "sv_indicatorWidth", 0f),
                drawable(themed, "themedCarBackground")
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun attr(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "attr", context.packageName)

    private fun drawable(context: Context, name: String): Drawable? {
        val id = attr(context, name)
        if (id == 0) return null
        val ta = context.theme.obtainStyledAttributes(intArrayOf(id))
        try {
            return ta.getDrawable(0)
        } catch (e: Exception) {
            return null
        } finally {
            ta.recycle()
        }
    }

    private fun color(context: Context, name: String, def: Int): Int {
        val id = attr(context, name)
        if (id == 0) return def
        val ta = context.theme.obtainStyledAttributes(intArrayOf(id))
        try {
            return ta.getColor(0, def)
        } catch (e: Exception) {
            return def
        } finally {
            ta.recycle()
        }
    }

    private fun dimension(context: Context, name: String, def: Float): Float {
        val id = attr(context, name)
        if (id == 0) return def
        val ta = context.theme.obtainStyledAttributes(intArrayOf(id))
        try {
            return ta.getDimension(0, def)
        } catch (e: Exception) {
            return def
        } finally {
            ta.recycle()
        }
    }
}
