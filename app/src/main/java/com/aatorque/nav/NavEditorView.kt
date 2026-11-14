package com.aatorque.nav

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.aatorque.obd.MazdaPids
import kotlin.math.hypot
import kotlin.math.max

/**
 * Предпросмотр раскладки на телефоне с управлением касанием: тянуть элемент - переместить,
 * кружок в правом нижнем углу выбранного элемента - изменить размер. Положения хранятся долями области.
 */
class NavEditorView(context: Context) : View(context) {
    /** Элементы по формам; список меняется на месте. */
    var forms: HashMap<String, ArrayList<NavItem>> = HashMap()
    var formKey: String = NavLayoutStore.WIDE
    var aspect: Float = 2.59f
        set(value) {
            field = value
            invalidate()
        }
    var selectedId: String? = null
    var onSelectionChanged: (() -> Unit)? = null

    private val painter = NavPainter()
    private val area = RectF()
    private val tmp = RectF()
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val linePaint = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true; strokeWidth = 1.5f * density }
    private var mode = MODE_NONE
    private var lastX = 0f
    private var lastY = 0f

    /** Тема предпросмотра (null - плоский вид) и деления. */
    fun setThemeRes(res: NavThemeRes?, ticks: Boolean) {
        painter.theme = res
        painter.ticksOn = ticks
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
        val items = forms[formKey] ?: return
        for (item in items) {
            if (item.kind == NavItem.GAUGE) {
                val shown = item.min + (item.max - item.min) * 0.45f
                painter.gauge(canvas, area, item, shown, painter.gaugeText(shown, item.max))
            } else {
                painter.info(canvas, area, item, sampleInfo(item))
            }
            // тонкая рамка ячейки, чтобы было видно, за что тянуть
            linePaint.color = Color.argb(80, 170, 185, 220)
            itemRect(item, tmp)
            canvas.drawRect(tmp, linePaint)
        }
        val sel = selectedItem()
        if (sel != null) {
            linePaint.color = Color.rgb(255, 190, 60)
            itemRect(sel, tmp)
            canvas.drawRect(tmp, linePaint)
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
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
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
                            val nx = (sel.x + dx).coerceIn(0f, max(0f, 1f - sel.w))
                            val ny = (sel.y + dy).coerceIn(0f, max(0f, 1f - sel.h))
                            list[idx] = sel.copy(x = nx, y = ny)
                        } else {
                            val nw = (sel.w + dx).coerceIn(MIN_SIZE, max(MIN_SIZE, 1f - sel.x))
                            val nh = (sel.h + dy).coerceIn(MIN_SIZE, max(MIN_SIZE, 1f - sel.y))
                            list[idx] = sel.copy(w = nw, h = nh)
                        }
                        invalidate()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = MODE_NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_MOVE = 1
        private const val MODE_RESIZE = 2
        private const val MIN_SIZE = 0.06f
        private const val HANDLE_DP = 11f
    }
}
