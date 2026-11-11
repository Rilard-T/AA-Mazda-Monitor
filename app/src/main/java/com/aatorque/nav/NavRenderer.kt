package com.aatorque.nav

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Приборы на реальных данных. Рисование идёт в своём потоке по сигналу вертикальной синхронизации,
 * показания сглажены, раскладка выбирается по форме стабильной области.
 */
class NavRenderer : SurfaceCallback {
    private class Gauge(
        val label: String,
        val unit: String,
        val id: String,
        val scale: Double,
        val min: Float,
        val max: Float
    ) {
        var shown = min
        var has = false
        var text = "--"
        var textKey = Int.MIN_VALUE
    }

    private class Info(val label: String, val id: String, val unit: String, val decimals: Int)

    private val gauges = listOf(
        Gauge("Скорость", "км/ч", "0d", 1.0, 0f, 240f),
        Gauge("Обороты", "x1000", "0c", 0.001, 0f, 6f),
        Gauge("Темп. ОЖ", "°C", "05", 1.0, 40f, 120f)
    )
    private val infos = listOf(
        Info("DPF", "ff9010", "%", 0),
        Info("Сажа", "ff9003", "г/л", 2),
        Info("АКПП", "ff9005", "°C", 0),
        Info("Интервал", "ff9011", "км", 0)
    )

    private val debugOverlay = false

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
            draw(dt)
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
        val th = HandlerThread("nav-render")
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

    private fun draw(dt: Float) {
        val s = surface ?: return
        if (!s.isValid) return
        val c = lock(s) ?: return
        try {
            c.drawColor(Color.rgb(8, 12, 28))
            val st = stable
            val area = if (st.width() > 0 && st.height() > 0) st else Rect(0, 0, width, height)
            val w = area.width().toFloat()
            val h = area.height().toFloat()

            // нижняя полоса с дополнительными значениями
            val footerH = min(max(h * 0.16f, 56f), h * 0.3f)
            val gh = h - footerH

            // сглаживание показаний: постоянная времени 0,12 с
            val k = 1f - exp(-dt / 0.12f)
            for (g in gauges) {
                val raw = NavData.get(g.id)
                g.has = raw != null
                val target = if (raw != null) (raw * g.scale).toFloat() else g.min
                g.shown += (target - g.shown) * k
            }

            // раскладка: ряд или колонка, берём ту, где приборы крупнее
            val rowBase = min(w / 6f, gh / 2f) * 0.9f
            val colBase = min(w / 2f, gh / 6f) * 0.9f
            val column = colBase > rowBase
            val base = if (column) colBase else rowBase
            val cell = if (column) gh / 3f else w / 3f
            for (i in gauges.indices) {
                val cx = if (column) area.exactCenterX() else area.left + cell * (i + 0.5f)
                val cy = if (column) area.top + cell * (i + 0.5f) else area.top + gh / 2f
                val r = if (i == 1) base else base * 0.85f
                drawGauge(c, cx, cy, r, gauges[i])
            }

            drawFooter(c, area.left.toFloat(), area.bottom - footerH, w, footerH)

            if (debugOverlay) {
                stroke.strokeWidth = 2f
                stroke.color = Color.CYAN
                c.drawRect(visible, stroke)
                stroke.color = Color.YELLOW
                c.drawRect(stable, stroke)
                text.color = Color.GRAY
                text.textSize = 22f
                text.textAlign = Paint.Align.LEFT
                c.drawText(debug, area.left + 12f, area.top + 26f, text)
                text.textAlign = Paint.Align.CENTER
            }
        } finally {
            try {
                s.unlockCanvasAndPost(c)
            } catch (e: Exception) {
            }
        }
    }

    private fun shownText(g: Gauge): String {
        if (!g.has) return "--"
        val tenths = g.max <= 10f
        val key = if (tenths) (g.shown * 10f).roundToInt() else g.shown.roundToInt()
        if (key != g.textKey) {
            g.textKey = key
            g.text = if (tenths) "${key / 10},${key % 10}" else key.toString()
        }
        return g.text
    }

    private fun format(v: Double?, decimals: Int): String {
        if (v == null) return "--"
        if (decimals == 0) return v.roundToInt().toString()
        val k = (v * 100.0).roundToInt()
        return "${k / 100},${(k % 100).toString().padStart(2, '0')}"
    }

    private fun drawFooter(c: Canvas, left: Float, top: Float, w: Float, h: Float) {
        for (j in infos.indices) {
            val inf = infos[j]
            val cx = left + w * (j + 0.5f) / infos.size
            text.color = Color.rgb(170, 185, 220)
            text.textSize = h * 0.26f
            c.drawText(inf.label, cx, top + h * 0.40f, text)
            val v = NavData.get(inf.id)
            text.color = Color.WHITE
            text.textSize = h * 0.40f
            c.drawText(if (v == null) "--" else format(v, inf.decimals) + " " + inf.unit, cx, top + h * 0.86f, text)
        }
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

        // подписи
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
