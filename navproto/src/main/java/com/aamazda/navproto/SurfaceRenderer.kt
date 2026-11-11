package com.aamazda.navproto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Три пробных прибора с имитацией данных. Раскладка считается по стабильной области, чтобы приборы не прыгали. */
class SurfaceRenderer : SurfaceCallback {
    private class Gauge(
        val label: String,
        val unit: String,
        val min: Float,
        val max: Float,
        val period: Float,
        val phase: Float,
        val step: Float
    )

    private val gauges = listOf(
        Gauge("Скорость", "км/ч", 0f, 240f, 17f, 0f, 20f),
        Gauge("Обороты", "x1000", 0f, 6f, 9f, 1.2f, 1f),
        Gauge("Темп. ОЖ", "°C", 40f, 120f, 31f, 2.4f, 10f)
    )

    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var dpi = 0
    private var visible = Rect()
    private var stable = Rect()
    private val handler = Handler(Looper.getMainLooper())
    private val rectF = RectF()

    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val text = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val ticker = object : Runnable {
        override fun run() {
            draw()
            handler.postDelayed(this, 33)
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surface = surfaceContainer.surface
        width = surfaceContainer.width
        height = surfaceContainer.height
        dpi = surfaceContainer.dpi
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        handler.removeCallbacks(ticker)
        surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        stable = Rect(stableArea)
    }

    private fun value(g: Gauge, t: Float): Float {
        val k = 0.5f + 0.5f * sin((2.0 * PI * t / g.period + g.phase).toDouble()).toFloat()
        return g.min + (g.max - g.min) * k
    }

    private fun draw() {
        val s = surface ?: return
        if (!s.isValid) return
        val c: Canvas = try {
            s.lockCanvas(null)
        } catch (e: Exception) {
            null
        } ?: return
        try {
            c.drawColor(Color.rgb(8, 12, 28))
            val area = if (stable.width() > 0 && stable.height() > 0) stable else Rect(0, 0, width, height)
            val t = SystemClock.uptimeMillis() / 1000f

            val cell = area.width() / 3f
            val base = min(cell / 2f, area.height() / 2f) * 0.9f
            val cy = area.exactCenterY()
            for (i in gauges.indices) {
                val cx = area.left + cell * (i + 0.5f)
                val r = if (i == 1) base else base * 0.85f
                drawGauge(c, cx, cy, r, gauges[i], value(gauges[i], t))
            }

            // отладочные рамки: голубая видимая, жёлтая стабильная
            stroke.strokeWidth = 2f
            stroke.color = Color.CYAN
            c.drawRect(visible, stroke)
            stroke.color = Color.YELLOW
            c.drawRect(stable, stroke)
            text.color = Color.GRAY
            text.textSize = 22f
            text.textAlign = Paint.Align.LEFT
            c.drawText(
                "${width}x$height $dpi dpi  visible ${visible.toShortString()}  stable ${stable.toShortString()}",
                area.left + 12f, area.bottom - 10f, text
            )
            text.textAlign = Paint.Align.CENTER
        } finally {
            s.unlockCanvasAndPost(c)
        }
    }

    private fun drawGauge(c: Canvas, cx: Float, cy: Float, r: Float, g: Gauge, v: Float) {
        val frac = ((v - g.min) / (g.max - g.min)).coerceIn(0f, 1f)

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
            val r1 = r * 0.70f
            val r2 = r * 0.78f
            c.drawLine(
                cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(),
                cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat(), stroke
            )
        }

        // стрелка
        val na = (135.0 + 270.0 * frac) * PI / 180.0
        stroke.strokeWidth = r * 0.035f
        stroke.color = Color.rgb(255, 190, 60)
        c.drawLine(cx, cy, cx + r * 0.66f * cos(na).toFloat(), cy + r * 0.66f * sin(na).toFloat(), stroke)
        fill.color = Color.rgb(255, 190, 60)
        c.drawCircle(cx, cy, r * 0.05f, fill)

        // подписи
        text.color = Color.rgb(170, 185, 220)
        text.textSize = r * 0.14f
        c.drawText(g.label, cx, cy - r * 0.30f, text)
        text.color = Color.WHITE
        text.textSize = r * 0.30f
        val shown = if (g.max <= 10f) String.format("%.1f", v) else v.toInt().toString()
        c.drawText(shown, cx, cy + r * 0.50f, text)
        text.color = Color.rgb(170, 185, 220)
        text.textSize = r * 0.13f
        c.drawText(g.unit, cx, cy + r * 0.68f, text)
    }
}
