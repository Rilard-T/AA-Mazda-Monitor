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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    private val painter = NavPainter()

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

    private val areaF = RectF()
    private var areaInit = false
    private var al = 0f
    private var at = 0f
    private var ar = 0f
    private var ab = 0f
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
                    painter.gauge(c, area, item, gs.shown, shownText(item, gs))
                } else {
                    painter.info(c, area, item, infoText(item))
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

    private fun infoText(item: NavItem): String {
        val v = NavData.get(item.pid) ?: return "--"
        return painter.formatValue(v * item.scale, item.decimals) + " " + item.unit
    }
}
