package com.aadash.nav

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
import com.aadash.stats.R
import com.aadash.obd.RegenTracker
import com.aadash.prefs.AppProfiles
import kotlin.math.exp
import kotlin.math.max
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
    private val painter = NavPainter().apply { appContext = context.applicationContext }
    private val fullF = RectF()
    private val bannerBand = RectF()
    private val bannerRect = RectF()
    private var loadedTheme: String? = null
    private var loadedFont: String? = null
    private var lastReportedAspect: Float = 0f
    private var regenTracker: RegenTracker? = null
    private var prevRegenStatus = 0
    private var regenDoneUntil = 0L
    private var bannerAlpha = 0f

    private var debugOverlay = false

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
    private val bannerFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val bannerText = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }

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
        if (surfaceContainer.width != width || surfaceContainer.height != height) areaInit = false
        surface = surfaceContainer.surface
        width = surfaceContainer.width
        height = surfaceContainer.height
        dpi = surfaceContainer.dpi
        if (regenTracker == null) {
            try {
                regenTracker = RegenTracker.get(appContext.getSharedPreferences(AppProfiles.obdPrefsName(appContext), Context.MODE_PRIVATE))
            } catch (e: Exception) {
                regenTracker = null
            }
        }
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
            val layout = NavLayoutStore.layout
            syncTheme(layout)
            fullF.set(0f, 0f, width.toFloat(), height.toFloat())
            painter.background(c, fullF)
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

            // реальные пропорции области - для предпросмотра в редакторе на телефоне
            val aspectNow = (tr - tl) / (tb - tt)
            if (kotlin.math.abs(aspectNow - lastReportedAspect) >= 0.02f) {
                lastReportedAspect = aspectNow
                NavLayoutStore.reportArea(appContext, aspectNow)
            }

            painter.pxScale = NavPainter.scaleFor(tr - tl, tb - tt)
            val idx = NavScreen.currentIndex
            if (idx <= 0 || layout.graphs.isEmpty()) {
                val narrow = (tr - tl) < (tb - tt) * layout.narrowAspect
                val form = layout.form(if (narrow) NavLayoutStore.NARROW else NavLayoutStore.WIDE)
                val k = 1f - exp(-dt / 0.12f)
                for (item in form.items) {
                    if (item.kind == NavItem.GAUGE) {
                        val gs = states.getOrPut(item.id) { GaugeState(item.min) }
                        val raw = NavData.get(item.pid)
                        gs.has = raw != null
                        val goal = if (raw != null) (raw * item.scale).toFloat() else item.min
                        gs.shown += (goal - gs.shown) * k
                        painter.gauge(c, area, item, gs.shown, shownText(item, gs))
                    } else if (item.kind == NavItem.MEDIA) {
                        painter.media(c, area, item, NavData.getText(item.pid) ?: "--")
                    } else {
                        painter.info(c, area, item, infoText(item))
                    }
                }
            } else {
                val g = layout.graphs.getOrNull(idx - 1)
                if (g != null) painter.graph(c, area, g)
            }

            // Статус прожига: плашка живёт в строке шапки Android Auto — там же, где «Выход»
            // и кнопки экранов, а не поверх приборов. Видна, пока блок сообщает статус 1 или 2;
            // сам статус дописывается в скобках. Включение, сдвиг и цвета — в редакторе раскладки.
            val banner = layout.regen
            val status = if (regenTracker?.regenStatusAvailable() == true) regenTracker?.regenStatus() ?: 0 else 0
            // Автоскрытие: после завершения прожига показываем «завершён» ещё 5 секунд.
            val now = System.currentTimeMillis()
            if (banner.enabled) {
                if ((prevRegenStatus == 1 || prevRegenStatus == 2) && status == 0 && regenDoneUntil == 0L) {
                    regenDoneUntil = now + 5000L
                }
                if (status == 1 || status == 2) regenDoneUntil = 0L
            }
            prevRegenStatus = status
            val showBanner = banner.enabled && ((status == 1 || status == 2) || (status == 0 && now < regenDoneUntil))
            val targetAlpha = if (showBanner) 1f else 0f
            val ka = 1f - kotlin.math.exp(-dt / 0.18f)
            bannerAlpha += (targetAlpha - bannerAlpha) * ka
            if (bannerAlpha > 0.01f) {
                // Видимая область начинается ниже шапки: полоса шапки — от верха поверхности до visible.top.
                if (banner.placement == RegenBanner.PLACEMENT_FIRST_ROW) {
                    bannerBand.set(area.left, area.top, area.right, area.top + RegenBannerPainter.height(banner, painter.pxScale) + 4f * painter.pxScale)
                } else {
                    bannerBand.set(0f, 0f, width.toFloat(), visible.top.toFloat().coerceAtLeast(0f))
                }
                val label = if (status == 1 || status == 2) appContext.getString(R.string.regen_banner_text) + " (" + status + ")" else appContext.getString(R.string.regen_banner_done)
                RegenBannerPainter.bounds(bannerBand, banner, painter.pxScale, label, bannerText, bannerRect)
                RegenBannerPainter.draw(c, bannerRect, banner, painter.pxScale, label, bannerFill, stroke, bannerText, bannerAlpha)
            }

            if (debugOverlay) {
                stroke.strokeWidth = 2f * painter.pxScale
                stroke.color = Color.CYAN
                c.drawRect(visible, stroke)
                stroke.color = Color.YELLOW
                c.drawRect(stable, stroke)
                text.color = Color.GRAY
                text.textSize = 22f * painter.pxScale
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

    /** Тема из раскладки: при смене названия загрузить картинки темы (пустое название - плоский вид). */
    private fun syncTheme(layout: NavLayout) {
        if (loadedTheme != layout.theme) {
            loadedTheme = layout.theme
            painter.theme = NavThemes.load(appContext, layout.theme)
        }
        painter.ticksOn = layout.ticks
        painter.textScale = layout.textScale
        if (loadedFont != layout.font) {
            loadedFont = layout.font
            painter.typeface = NavFonts.load(appContext, layout.font)
        }
        debugOverlay = layout.debug
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
