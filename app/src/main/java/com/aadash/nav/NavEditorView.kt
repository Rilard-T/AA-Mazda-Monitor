package com.aadash.nav

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import com.aadash.obd.MazdaPids
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Предпросмотр раскладки на телефоне с управлением касанием: тянуть элемент - переместить,
 * кружок в правом нижнем углу выбранного элемента - изменить размер. Положения хранятся долями области.
 */
class NavEditorView(context: Context) : View(context) {
    /** Элементы по формам; список меняется на месте. */
    var forms: HashMap<String, ArrayList<NavItem>> = HashMap()
    var formKey: String = NavLayoutStore.WIDE
    var aspect: Float = NavAspects.DEFAULT
        set(value) {
            field = value
            invalidate()
        }
    var selectedId: String? = null
    var onSelectionChanged: (() -> Unit)? = null

    /** Удержание пальца на элементе: Activity показывает меню элемента. */
    var onLongPressItem: ((NavItem) -> Unit)? = null

    var gridOn: Boolean = true
    var snapToGrid: Boolean = true
    var previewGraph: GraphDef? = null
    var gridCols: Int = 12
    var gridRows: Int = 8
    /** Уведомление о прожиге: в предпросмотре показывается всегда, чтобы были видны место и цвета. */
    var regen: RegenBanner = RegenBanner.DEFAULT
        set(value) {
            field = value
            invalidate()
        }

    private val painter = NavPainter().apply { appContext = context.applicationContext }
    private val area = RectF()
    private val tmp = RectF()
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val linePaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true; strokeWidth = 1.5f * density }
    private val textPaint = Paint().apply { isAntiAlias = true }
    private var mode = MODE_NONE
    private var lastX = 0f
    private var lastY = 0f
    private val pressHandler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private val longPress = Runnable {
        val sel = selectedItem()
        if (sel != null && mode == MODE_MOVE && !moved) {
            mode = MODE_NONE
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPressItem?.invoke(sel)
        }
    }

    /** Тема предпросмотра (null - плоский вид) и деления. */
    fun setThemeRes(res: NavThemeRes?, ticks: Boolean) {
        painter.theme = res
        painter.ticksOn = ticks
        invalidate()
    }

    fun setFont(typeface: Typeface?) {
        painter.typeface = typeface
        invalidate()
    }

    fun selectedItem(): NavItem? {
        val id = selectedId ?: return null
        return forms[formKey]?.firstOrNull { it.id == id }
    }

    /** Заменить выбранный элемент (например, после смены датчика). */
    fun replaceSelected(item: NavItem) {
        val list = forms[formKey] ?: return
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            list[idx] = item
            invalidate()
            onSelectionChanged?.invoke()
        }
    }

    private fun computeArea() {
        val pad = 12f * density
        val availW = max(1f, width - 2f * pad)
        val availH = max(1f, height - 2f * pad)
        val w: Float
        val h: Float
        if (availW / availH > aspect) {
            h = availH
            w = h * aspect
        } else {
            w = availW
            h = w / aspect
        }
        val l = (width - w) / 2f
        val t = (height - h) / 2f
        area.set(l, t, l + w, t + h)
    }

    private fun itemRect(item: NavItem, out: RectF) {
        out.set(
            area.left + item.x * area.width(),
            area.top + item.y * area.height(),
            area.left + (item.x + item.w) * area.width(),
            area.top + (item.y + item.h) * area.height()
        )
    }

    private fun sampleInfo(item: NavItem): String {
        val pd = MazdaPids.byId(item.pid)
        val mid = if (pd != null) (pd.min + pd.max) / 2.0 else 0.0
        return painter.formatValue(mid * item.scale, item.decimals) + " " + item.unit
    }

    override fun onDraw(canvas: Canvas) {
        computeArea()
        canvas.drawColor(Color.rgb(32, 32, 36))
        painter.background(canvas, area)
        val pg = previewGraph
        if (pg != null) {
            painter.graph(canvas, area, pg)
            linePaint.color = Color.GRAY
            canvas.drawRect(area, linePaint)
            return
        }
        // сетка
        if (gridOn) {
            linePaint.color = Color.argb(50, 120, 140, 200)
            linePaint.strokeWidth = 1f * density
            for (i in 1 until gridCols) {
                val x = area.left + area.width() * i / gridCols
                canvas.drawLine(x, area.top, x, area.bottom, linePaint)
            }
            for (i in 1 until gridRows) {
                val y = area.top + area.height() * i / gridRows
                canvas.drawLine(area.left, y, area.right, y, linePaint)
            }
        }
        val items = forms[formKey] ?: return
        for (item in items) {
            if (item.kind == NavItem.GAUGE) {
                val shown = item.min + (item.max - item.min) * 0.45f
                painter.gauge(canvas, area, item, shown, painter.gaugeText(shown, item.max))
            } else if (item.kind == NavItem.MEDIA) {
                painter.media(canvas, area, item, "Artist — Title")
            } else {
                painter.info(canvas, area, item, sampleInfo(item))
            }
            // рамка ячейки
            itemRect(item, tmp)
            val border = item.border.ifEmpty { if (item.kind == NavItem.GAUGE) "circle" else "square" }
            linePaint.color = Color.argb(80, 170, 185, 220)
            linePaint.strokeWidth = 1.5f * density
            when (border) {
                "circle" -> {
                    val r = min(tmp.width(), tmp.height()) / 2f
                    canvas.drawCircle(tmp.centerX(), tmp.centerY(), r, linePaint)
                }
                "none" -> {}
                else -> canvas.drawRect(tmp, linePaint)
            }
        }
        if (regen.enabled) {
            RegenBannerPainter.draw(
                canvas, area, regen, density,
                context.getString(com.aadash.stats.R.string.regen_banner_text),
                fillPaint, linePaint, textPaint
            )
        }
        val sel = selectedItem()
        if (sel != null) {
            linePaint.color = Color.rgb(255, 190, 60)
            itemRect(sel, tmp)
            val border = sel.border.ifEmpty { if (sel.kind == NavItem.GAUGE) "circle" else "square" }
            if (border == "circle") {
                val r = min(tmp.width(), tmp.height()) / 2f
                canvas.drawCircle(tmp.centerX(), tmp.centerY(), r, linePaint)
            } else if (border != "none") {
                canvas.drawRect(tmp, linePaint)
            }
            fillPaint.color = Color.rgb(255, 190, 60)
            canvas.drawCircle(tmp.right, tmp.bottom, HANDLE_DP * density, fillPaint)
        }
        linePaint.color = Color.GRAY
        canvas.drawRect(area, linePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val items = forms[formKey] ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = MODE_NONE
                lastX = event.x
                lastY = event.y
                val sel = selectedItem()
                if (sel != null) {
                    itemRect(sel, tmp)
                    if (hypot(event.x - tmp.right, event.y - tmp.bottom) <= HANDLE_DP * density * 2f) mode = MODE_RESIZE
                }
                if (mode == MODE_NONE) {
                    var hit: NavItem? = null
                    for (i in items.indices.reversed()) {
                        itemRect(items[i], tmp)
                        if (tmp.contains(event.x, event.y)) {
                            hit = items[i]
                            break
                        }
                    }
                    selectedId = hit?.id
                    if (hit != null) mode = MODE_MOVE
                    onSelectionChanged?.invoke()
                }
                downX = event.x
                downY = event.y
                moved = false
                pressHandler.removeCallbacks(longPress)
                if (mode == MODE_MOVE) {
                    pressHandler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved) {
                    if (hypot(event.x - downX, event.y - downY) <= slop) return true
                    moved = true
                    pressHandler.removeCallbacks(longPress)
                    lastX = downX
                    lastY = downY
                }
                val sel = selectedItem()
                if (sel != null && mode != MODE_NONE && area.width() > 0f && area.height() > 0f) {
                    val dx = (event.x - lastX) / area.width()
                    val dy = (event.y - lastY) / area.height()
                    lastX = event.x
                    lastY = event.y
                    val list = forms[formKey]
                    val idx = list?.indexOfFirst { it.id == sel.id } ?: -1
                    if (list != null && idx >= 0) {
                        if (mode == MODE_MOVE) {
                            var nx = (sel.x + dx).coerceIn(0f, max(0f, 1f - sel.w))
                            var ny = (sel.y + dy).coerceIn(0f, max(0f, 1f - sel.h))
                            if (snapToGrid) {
                                val gx = 1f / gridCols; val gy = 1f / gridRows
                                nx = (nx / gx).roundToInt() * gx
                                ny = (ny / gy).roundToInt() * gy
                                nx = nx.coerceIn(0f, max(0f, 1f - sel.w))
                                ny = ny.coerceIn(0f, max(0f, 1f - sel.h))
                            }
                            list[idx] = sel.copy(x = nx, y = ny)
                        } else {
                            var nw = (sel.w + dx).coerceIn(MIN_SIZE, max(MIN_SIZE, 1f - sel.x))
                            var nh = (sel.h + dy).coerceIn(MIN_SIZE, max(MIN_SIZE, 1f - sel.y))
                            if (snapToGrid) {
                                val gx = 1f / gridCols; val gy = 1f / gridRows
                                nw = (nw / gx).roundToInt() * gx
                                nh = (nh / gy).roundToInt() * gy
                                if (nw < MIN_SIZE) nw = gx
                                if (nh < MIN_SIZE) nh = gy
                            }
                            list[idx] = sel.copy(w = nw, h = nh)
                        }
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressHandler.removeCallbacks(longPress)
                mode = MODE_NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        pressHandler.removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_MOVE = 1
        private const val MODE_RESIZE = 2
        private const val MIN_SIZE = 0.06f
        private const val HANDLE_DP = 11f
    }
}
