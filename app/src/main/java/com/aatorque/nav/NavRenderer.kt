package com.aatorque.nav

import android.content.Context
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
 * показания сглажены. Что и где рисовать, берётся из раскладки (NavLayoutStore) в долях видимой области;
 * форма области (широкая или узкая колонка) выбирается по её пропорциям.
 */
class NavRenderer(context: Context) : SurfaceCallback {
    private val appContext: Context = context.applicationContext

    /** Сглаженное показание прибора (по id элемента раскладки). */
    private class GaugeState(var shown: Float) {
        var has = false
        var text = "--"
        var textKey = Int.MIN_VALUE
    }

    private val states = HashMap<String, GaugeState>()

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
    private val areaF = RectF()
    private var areaInit = false
    private var al = 0f
    private var at = 0f
    private var ar = 0f
    private var ab = 0f
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
        NavLayoutStore.load(appContext)
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
        areaInit = false
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
            // раскладка по видимой области; при её изменении границы плавно перетекают
            val vs = visible
            val st = stable
            val target = when {
                vs.width() > 0 && vs.height() > 0 -> vs
                st.width() > 0 && st.height() > 0 -> st
                else -> Rect(0, 0, width, height)
            }
            val tl = target.left.toFloat().coerceIn(0f, width.toFloat())
            val tt = target.top.toFloat().coerceIn(0f, height.toFloat())
            val tr = target.right.toFloat().coerceIn(tl + 1f, max(width.toFloat(), tl + 1f))
            val tb = target.bottom.toFloat().coerceIn(tt + 1f, max(height.toFloat(), tt + 1f))
            if (!areaInit) {
                areaInit = true
                al = tl; at = tt; ar = tr; ab = tb
            } else {
                val ka = 1f - exp(-dt / 0.25f)
                al += (tl - al) * ka
                at += (tt - at) * ka
                ar += (tr - ar) * ka
                ab += (tb - ab) * ka
            }
            areaF.set(al, at, ar, ab)
            val area = areaF

            // форма области: узкая колонка или широкая, по пропорциям целевой области
            val layout = NavLayoutStore.layout
            val narrow = (tr - tl) < (tb - tt) * layout.narrowAspect
            val form = layout.form(if (narrow) NavLayoutStore.NARROW else NavLayoutStore.WIDE)

            // сглаживание показаний: постоянная времени 0,12 с
            val k = 1f - exp(-dt / 0.12f)
            for (item in form.items) {
                if (item.kind == NavItem.GAUGE) {
                    val gs = states.getOrPut(item.id) { GaugeState(item.min) }
                    val raw = NavData.get(item.pid)
                    gs.has = raw != null
                    val goal = if (raw != null) (raw * item.scale).toFloat() else item.min
                    gs.shown += (goal - gs.shown) * k
                    drawGauge(c, area, item, gs)
                } else {
                    drawInfo(c, area, item)
                }
            }

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

    private fun shownText(item: NavItem, gs: GaugeState): String {
        if (!gs.has) return "--"
        val tenths = item.max <= 10f
        val key = if (tenths) (gs.shown * 10f).roundToInt() else gs.shown.roundToInt()
        if (key != gs.textKey) {
            gs.textKey = key
            gs.text = if (tenths) "${key / 10},${key % 10}" else key.toString()
        }
        return gs.text
    }

    private fun format(v: Double?, decimals: Int): String {
        if (v == null) return "--"
        if (decimals == 0) return v.roundToInt().toString()
        val k = (v * 100.0).roundToInt()
        return "${k / 100},${(k % 100).toString().padStart(2, '0')}"
    }

    /** Текстовое значение: подпись сверху, число и единица снизу, в своей ячейке. */
    private fun drawInfo(c: Canvas, area: RectF, item: NavItem) {
        val left = area.left + item.x * area.width()
        val top = area.top + item.y * area.height()
        val w = item.w * area.width()
        val h = item.h * area.height()
        if (w <= 1f || h <= 1f) return
        val cx = left + w / 2f
        text.color = Color.rgb(170, 185, 220)
        text.textSize = min(h * 0.26f, w / 7f)
        c.drawText(item.label, cx, top + h * 0.40f, text)
        val v = NavData.get(item.pid)
        text.color = Color.WHITE
        text.textSize = min(h * 0.40f, w / 5f)
        c.drawText(if (v == null) "--" else format(v * item.scale, item.decimals) + " " + item.unit, cx, top + h * 0.86f, text)
    }

    /** Прибор: круг вписан в свою ячейку, размер круга задаёт item.size. */
    private fun drawGauge(c: Canvas, area: RectF, item: NavItem, gs: GaugeState) {
        val cx = area.left + (item.x + item.w / 2f) * area.width()
        val cy = area.top + (item.y + item.h / 2f) * area.height()
        val r = min(item.w * area.width(), item.h * area.height()) / 2f * item.size
        if (r <= 1f) return
        val frac = ((gs.shown - item.min) / (item.max - item.min)).coerceIn(0f, 1f)

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
        c.drawText(shownText(item, gs), cx, cy + r * 0.45f, text)
        text.color = Color.rgb(170, 185, 220)
        text.textSize = r * 0.13f
        c.drawText(item.unit, cx, cy + r * 0.62f, text)
        text.textSize = r * 0.14f
        c.drawText(item.label, cx, cy + r * 0.86f, text)
    }
}
