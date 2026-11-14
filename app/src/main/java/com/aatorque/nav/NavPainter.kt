package com.aatorque.nav

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Рисование элементов раскладки в заданной области. Общий код для экрана в машине (NavRenderer)
 * и предпросмотра в редакторе на телефоне (NavEditorView), поэтому вид совпадает.
 * Положение и размер элемента берутся из долей области, значения и тексты передаёт вызывающий.
 */
class NavPainter {
    private val rectF = RectF()
    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val text = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }

    /** Число с запятой: decimals = 0 - целое, иначе две цифры после запятой. */
    fun formatValue(v: Double?, decimals: Int): String {
        if (v == null) return "--"
        if (decimals == 0) return v.roundToInt().toString()
        val k = (v * 100.0).roundToInt()
        return "${k / 100},${(k % 100).toString().padStart(2, '0')}"
    }

    /** Текст показания прибора: до 10 - с десятыми, иначе целое. */
    fun gaugeText(shown: Float, max: Float): String {
        val tenths = max <= 10f
        val key = if (tenths) (shown * 10f).roundToInt() else shown.roundToInt()
        return if (tenths) "${key / 10},${key % 10}" else key.toString()
    }

    /** Текстовое значение: подпись сверху, готовый текст (число и единица) снизу, в своей ячейке. */
    fun info(c: Canvas, area: RectF, item: NavItem, valueText: String) {
        val left = area.left + item.x * area.width()
        val top = area.top + item.y * area.height()
        val w = item.w * area.width()
        val h = item.h * area.height()
        if (w <= 1f || h <= 1f) return
        val cx = left + w / 2f
        text.color = Color.rgb(170, 185, 220)
        text.textSize = min(h * 0.26f, w / 7f)
        c.drawText(item.label, cx, top + h * 0.40f, text)
        text.color = Color.WHITE
        text.textSize = min(h * 0.40f, w / 5f)
        c.drawText(valueText, cx, top + h * 0.86f, text)
    }

    /** Прибор: круг вписан в свою ячейку, размер круга задаёт item.size. */
    fun gauge(c: Canvas, area: RectF, item: NavItem, shown: Float, valueText: String) {
        val cx = area.left + (item.x + item.w / 2f) * area.width()
        val cy = area.top + (item.y + item.h / 2f) * area.height()
        val r = min(item.w * area.width(), item.h * area.height()) / 2f * item.size
        if (r <= 1f) return
        val frac = ((shown - item.min) / (item.max - item.min)).coerceIn(0f, 1f)

        fill.color = Color.rgb(18, 26, 54)
        c.drawCircle(cx, cy, r, fill)

        val ring = r * 0.86f
        rectF.set(cx - ring, cy - ring, cx + ring, cy + ring)
        stroke.strokeWidth = r * 0.07f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = Color.rgb(40, 52, 90)
        c.drawArc(rectF, 135f, 270f, false, stroke)
        stroke.color = if (frac > 0.85f) Color.rgb(255, 80, 60) else Color.rgb(60, 170, 255)
        c.drawArc(rectF, 135f, 270f * frac, false, stroke)
        stroke.strokeCap = Paint.Cap.BUTT

        // деления
        stroke.strokeWidth = r * 0.02f
        stroke.color = Color.rgb(170, 185, 220)
        val ticks = 10
        for (i in 0..ticks) {
            val a = (135.0 + 270.0 * i / ticks) * PI / 180.0
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            c.drawLine(cx + r * 0.70f * ca, cy + r * 0.70f * sa, cx + r * 0.78f * ca, cy + r * 0.78f * sa, stroke)
        }

        // стрелка
        val na = (135.0 + 270.0 * frac) * PI / 180.0
        stroke.strokeWidth = r * 0.035f
        stroke.color = Color.rgb(255, 190, 60)
        c.drawLine(cx, cy, cx + r * 0.66f * cos(na).toFloat(), cy + r * 0.66f * sin(na).toFloat(), stroke)
        fill.color = Color.rgb(255, 190, 60)
        c.drawCircle(cx, cy, r * 0.05f, fill)

        // подписи
        text.color = Color.WHITE
        text.textSize = r * 0.30f
        c.drawText(valueText, cx, cy + r * 0.45f, text)
        text.color = Color.rgb(170, 185, 220)
        text.textSize = r * 0.13f
        c.drawText(item.unit, cx, cy + r * 0.62f, text)
        text.textSize = r * 0.14f
        c.drawText(item.label, cx, cy + r * 0.86f, text)
    }
}
