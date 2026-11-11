package com.aamazda.navproto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.HandlerThread
import android.os.Handler
import android.view.Choreographer
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Три пробных прибора с имитацией данных.
 * Рисование идёт в отдельном потоке по сигналу вертикальной синхронизации (Choreographer),
 * стрелки сглажены, раскладка выбирается по форме стабильной области.
 */
class SurfaceRenderer : SurfaceCallback {
    private class Gauge(
        val label: String,
        val unit: String,
        val min: Float,
        val max: Float,
        val period: Float,
        val phase: Float
    ) {
        var shown = min
        var text = ""
        var textKey = Int.MIN_VALUE
    }

    private val gauges = listOf(
        Gauge("Скорость", "км/ч", 0f, 240f, 17f, 0f),
        Gauge("Обороты", "x1000", 0f, 6f, 9f, 1.2f),
        Gauge("Темп. ОЖ", "°C", 40f, 120f, 31f, 2.4f)
    )

    @Volatile private var surface: Surface? = null
    @Volatile private var width = 0
    @Volatile private var height = 0
    @Volatile private var dpi = 0
    @Volatile private var visible = Rect()
    @Volatile private var stable = Rect()
    @Volatile private var debug = ""

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var choreographer: Choreographer? = null
    @Volatile private var running = false
    private var lastNanos = 0L
    private var hardware = true

    private val rectF = RectF()
    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val text = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastNanos == 0L) 0.016f
            else ((frameTimeNanos - lastNanos) / 1e9f).coerceIn(0.001f, 0.1f)
            lastNanos = frameTimeNanos
            draw(frameTimeNanos / 1e9f, dt)
            choreographer?.postFrameCallback(this)
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surface = surfaceContainer.surface
        width = surfaceContainer.width
        height = surfaceContainer.height
        dpi = surfaceContainer.dpi
        updateDebug()
        start()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        stop()
        surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        updateDebug()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        stable = Rect(stableArea)
        updateDebug()
    }

    private fun updateDebug() {
        debug = "${width}x$height $dpi dpi  visible ${visible.toShortString()}  stable ${stable.toShortString()}"
    }

    private fun start() {
        if (running) return
        running = true
        lastNanos = 0L
        val th = HandlerThread("navproto-render")
        th.start()
        thread = th
        val h = Handler(th.looper)
        handler = h
        h.post {
            choreographer = Choreographer.getInstance()
            choreographer?.postFrameCallback(frame)
        }
    }

    private fun stop() {
        running = false
        handler?.post { choreographer?.removeFrameCallback(frame) }
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun lock(s: Surface): Canvas? {
        if (hardware) {
            try {
                return s.lockHardwareCanvas()
            } catch (e: Exception) {
                hardware = false
            }
        }
        return try {
            s.lockCanvas(null)
        } catch (e: Exception) {
            null
        }
    }

    private fun target(g: Gauge, t: Float): Float {
        val k = 0.5f + 0.5f * sin((2.0 * PI * t / g.period + g.phase).toDouble()).toFloat()
        return g.min + (g.max - g.min) * k
    }

    private fun draw(t: Float, dt: Float) {
        val s = surface ?: return
        if (!s.isValid) return
        val c = lock(s) ?: return
        try {
            c.drawColor(Color.rgb(8, 12, 28))
            val st = stable
            val area = if (st.width() > 0 && st.height() > 0) st else Rect(0, 0, width, height)
            val w = area.width().toFloat()
            val h = area.height().toFloat()

            // сглаживание показаний: постоянная времени 0,1 с
            val k = 1f - exp(-dt / 0.10f)
            for (g in gauges) g.shown += (target(g, t) - g.shown) * k

            // выбор раскладки: ряд или колонка, берём ту, где приборы крупнее
            val rowBase = min(w / 6f, h / 2f) * 0.9f
            val colBase = min(w / 2f, h / 6f) * 0.9f
            val column = colBase > rowBase
            val base = if (column) colBase else rowBase
            val cell = if (column) h / 3f else w / 3f
            for (i in gauges.indices) {
                val cx = if (column) area.exactCenterX() else area.left + cell * (i + 0.5f)
                val cy = if (column) area.top + cell * (i + 0.5f) else area.exactCenterY()
                val r = if (i == 1) base else base * 0.85f
                drawGauge(c, cx, cy, r, gauges[i])
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
            c.drawText(debug, area.left + 12f, area.bottom - 10f, text)
            text.textAlign = Paint.Align.CENTER
        } finally {
            try {
                s.unlockCanvasAndPost(c)
            } catch (e: Exception) {
            }
        }
    }

    private fun shownText(g: Gauge): String {
        val tenths = g.max <= 10f
        val key = if (tenths) (g.shown * 10f).roundToInt() else g.shown.roundToInt()
        if (key != g.textKey) {
            g.textKey = key
            g.text = if (tenths) "${key / 10},${key % 10}" else key.toString()
        }
        return g.text
    }

    private fun drawGauge(c: Canvas, cx: Float, cy: Float, r: Float, g: Gauge) {
        val frac = ((g.shown - g.min) / (g.max - g.min)).coerceIn(0f, 1f)

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

        // подписи: значение под центром, единицы и название в нижней «пустой» части циферблата
        text.color = Color.WHITE
        text.textSize = r * 0.30f
        c.drawText(shownText(g), cx, cy + r * 0.45f, text)
        text.color = Color.rgb(170, 185, 220)
        text.textSize = r * 0.13f
        c.drawText(g.unit, cx, cy + r * 0.62f, text)
        text.textSize = r * 0.14f
        c.drawText(g.label, cx, cy + r * 0.86f, text)
    }
}
