package com.aadash.nav

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import com.aadash.obd.MazdaPids
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Рисование элементов раскладки в заданной области. Общий код для экрана в машине (NavRenderer)
 * и предпросмотра в редакторе на телефоне (NavEditorView), поэтому вид совпадает.
 * Положение и размер элемента берутся из долей области, значения и тексты передаёт вызывающий.
 * Если задана тема (theme), циферблат и стрелка рисуются картинками темы из старого интерфейса.
 */
class NavPainter {
    var appContext: android.content.Context? = null
    /** Тема из старого интерфейса; null - плоский вид. */
    var theme: NavThemeRes? = null

    /** Деления: циферблат с делениями (true) или пустой (false). Только для темы. */
    var ticksOn: Boolean = true
    /** Масштаб под разрешение экрана: 1 при опорной высоте ~800 px. */
    var pxScale: Float = 1f

    /** Шрифт подписей приборов (null - системный). */
    var typeface: Typeface? = null
        set(value) {
            field = value
            text.typeface = value
        }

    private val rectF = RectF()
    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val stroke = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val text = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }

    /** Фон области: тёмный цвет, поверх него фон темы, если он есть. */
    fun background(c: Canvas, rect: RectF) {
        fill.color = Color.rgb(8, 12, 28)
        c.drawRect(rect, fill)
        val d = theme?.carBackground
        if (d != null) {
            d.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            d.draw(c)
        }
    }

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

    /** Медиа: подпись сверху, название трека снизу (одна строка, при необходимости уменьшенный шрифт). */
    fun media(c: Canvas, area: RectF, item: NavItem, valueText: String) {
        val left = area.left + item.x * area.width()
        val top = area.top + item.y * area.height()
        val w = item.w * area.width()
        val h = item.h * area.height()
        if (w <= 1f || h <= 1f) return
        val cx = left + w / 2f
        text.color = Color.rgb(170, 185, 220)
        text.textSize = min(h * 0.26f, w / 7f)
        c.drawText(item.label.ifBlank { "Трек" }, cx, top + h * 0.34f, text)
        text.color = Color.WHITE
        var size = min(h * 0.34f, w / 6f)
        while (size > 9f * pxScale && text.measureText(valueText) > w * 0.92f) {
            size -= 1f * pxScale
            text.textSize = size
        }
        text.textSize = size
        c.drawText(valueText, cx, top + h * 0.82f, text)
    }

    /** Прибор: круг (квадрат картинки темы) вписан в свою ячейку, размер задаёт item.size. */
    fun gauge(c: Canvas, area: RectF, item: NavItem, shown: Float, valueText: String) {
        val cx = area.left + (item.x + item.w / 2f) * area.width()
        val cy = area.top + (item.y + item.h / 2f) * area.height()
        val r = min(item.w * area.width(), item.h * area.height()) / 2f * item.size
        if (r <= 1f) return
        val frac = ((shown - item.min) / (item.max - item.min)).coerceIn(0f, 1f)
        val redFrac = if (item.red > item.min && item.red < item.max)
            (item.red - item.min) / (item.max - item.min) else -1f
        val th = theme
        if (th != null && (th.dialTicks != null || th.dialEmpty != null)) {
            val divisions = GaugeScale.ticks(item.max - item.min, item.min).second - 1
            themedDial(c, cx, cy, r, th, frac, divisions, redFrac)
            if (ticksOn && th.dialTicks != null) tickLabels(c, cx, cy, r, item, th)
        } else {
            flatDial(c, cx, cy, r, frac, redFrac)
        }
        gaugeTexts(c, cx, cy, r, item, valueText)
    }

    /** Плоский вид (без темы): тёмный круг, дуга, деления и стрелка. */
    private fun flatDial(c: Canvas, cx: Float, cy: Float, r: Float, frac: Float, redFrac: Float = -1f) {
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
        drawRedZone(c, cx, cy, r, redFrac)

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
    }

    /**
     * Вид темы: картинка циферблата на весь квадрат прибора, поверх неё стрелка. Как в старом интерфейсе,
     * стрелка-картинка занимает тот же квадрат и поворачивается вокруг центра от 225 градусов (минимум) на 270.
     * Если в теме нет картинки стрелки, рисуется линия цвета индикатора темы (приближённо).
     */
    private fun themedDial(
        c: Canvas, cx: Float, cy: Float, r: Float, th: NavThemeRes, frac: Float, divisions: Int,
        redFrac: Float = -1f
    ) {
        // Циферблат темы нарисован под 8 делений (как у стандартных шкал). Если делений другое число
        // (например, отсечка оборотов 0..6), риски темы не совпадут с подписями — берём пустой
        // циферблат и рисуем деления сами.
        // Циферблат темы нарисован под 8 делений. Для другой шкалы (обороты 0..6, скорость 240 и т.п.)
        // используем пустой циферблат (фон без рисок) и рисуем риски сами — так они не пропадают.
        // Риски должны совпадать с подписями. Родной диал темы нарисован под 8 делений,
        // поэтому для другой шкалы (0..6, 0..180) используем пустой циферблат и рисуем риски сами.
        // Риски рисуем только на пустом циферблате: на темах без пустого (dialTicks) у них
        // уже есть свои риски, иначе происходит наслоение.
        val useEmpty = ticksOn && th.dialEmpty != null
        val dial = if (useEmpty) th.dialEmpty else th.dialTicks
        val l = (cx - r).toInt()
        val t = (cy - r).toInt()
        val rr = (cx + r).toInt()
        val b = (cy + r).toInt()
        if (dial != null) {
            dial.setBounds(l, t, rr, b)
            dial.draw(c)
        }
        if (useEmpty) drawTicks(c, cx, cy, r, divisions)
        drawRedZone(c, cx, cy, r, redFrac)
        val needle = th.needle
        if (th.imageNeedle && needle != null) {
            c.save()
            c.rotate(225f + 270f * frac, cx, cy)
            needle.setBounds(l, t, rr, b)
            needle.draw(c)
            c.restore()
        } else {
            val color = if (Color.alpha(th.indicatorColor) < 128) th.needleColor else th.indicatorColor
            val a = (135.0 + 270.0 * frac) * PI / 180.0
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            stroke.color = color
            stroke.strokeWidth = max(th.indicatorWidth, r * 0.03f)
            stroke.strokeCap = Paint.Cap.ROUND
            c.drawLine(cx + r * 0.30f * ca, cy + r * 0.30f * sa, cx + r * 0.80f * ca, cy + r * 0.80f * sa, stroke)
            stroke.strokeCap = Paint.Cap.BUTT
            fill.color = color
            c.drawCircle(cx, cy, r * 0.04f, fill)
        }
    }

    /** Красная зона (отсечка): дуга от redFrac до конца шкалы, поверх циферблата, под стрелкой. */
    private fun drawRedZone(c: Canvas, cx: Float, cy: Float, r: Float, redFrac: Float) {
        if (redFrac <= 0f || redFrac >= 1f) return
        val start = 135f + 270f * redFrac
        rectF.set(cx - r * 0.86f, cy - r * 0.86f, cx + r * 0.86f, cy + r * 0.86f)
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeWidth = r * 0.05f
        stroke.color = Color.rgb(235, 70, 55)
        c.drawArc(rectF, start, 135f + 270f - start, false, stroke)
    }

    /** Деления шкалы, когда циферблат темы пустой или его рисок не хватает под текущую шкалу. */
    private fun drawTicks(c: Canvas, cx: Float, cy: Float, r: Float, divisions: Int) {
        if (divisions <= 0) return
        // Плотное кольцо рисок: между двумя соседними цифрами —
        // 3 риски: 2 маленькие и 1 средняя (больше маленьких, но меньше основной).
        // Цифра попадает на 5-ю риску от предыдущей (индекс 4).
        val subdiv = 4
        val total = divisions * subdiv
        stroke.strokeCap = Paint.Cap.BUTT
        for (i in 0..total) {
            val pos = i % subdiv
            val major = (pos == 0)
            val medium = (pos == subdiv / 2)
            val a = (135.0 + 270.0 * i / total) * PI / 180.0
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            val inner = when {
                major -> r * 0.72f
                medium -> r * 0.76f
                else -> r * 0.80f
            }
            stroke.color = Color.WHITE
            stroke.strokeWidth = when {
                major -> r * 0.04f
                medium -> r * 0.03f
                else -> r * 0.02f
            }
            c.drawLine(cx + inner * ca, cy + inner * sa, cx + r * 0.84f * ca, cy + r * 0.84f * sa, stroke)
        }
    }

    private companion object {
        /** Столько делений нарисовано на циферблатах тем (картинка диала фиксированная). */
        const val NATIVE_DIVISIONS = 8
    }

    /**
     * Числа шкалы у делений, внутри круга. Число подписей и значения берутся из min/max прибора
     * (в масштабе прибора: обороты в тысячах, своя отсечка у каждой машины). Угол i-й подписи
     * 135 + 270*i/(N-1), как в старом интерфейсе; N = 9, а при небольшом диапазоне (целом или
     * с шагом 0,5) шаг подписей 1 или 0,5. Удалённость от центра приближённо берётся из
     * отступа sv_tickPadding темы.
     */
    private fun tickLabels(c: Canvas, cx: Float, cy: Float, r: Float, item: NavItem, th: NavThemeRes) {
        val range = item.max - item.min
        if (range <= 0f) return
        val (step, n) = GaugeScale.ticks(range, item.min)
        val intLabels = abs(step - step.roundToInt()) < 1e-4f && abs(item.min - item.min.roundToInt()) < 1e-4f
        val radius = r * (0.72f - th.tickPaddingDp * 0.008f).coerceIn(0.50f, 0.78f)
        text.color = Color.WHITE
        text.textSize = max(r * 0.085f, 9f * pxScale)
        for (i in 0 until n) {
            val v = item.min + step * i
            val deg = 135f + 270f * i / (n - 1)
            val a = deg * PI / 180.0
            val x = cx + radius * cos(a).toFloat()
            val y = cy + radius * sin(a).toFloat()
            val s = if (intLabels) v.roundToInt().toString() else "%.1f".format(Locale.US, v).replace('.', ',')
            c.save()
            if (th.tickRotation) c.rotate(deg + 90f, x, y)
            c.drawText(s, x, y + text.textSize * 0.35f, text)
            c.restore()
        }
    }

    /** График (одиночный или совмещённый): линии по истории pid, сетка, легенда. */
    fun graph(c: Canvas, area: RectF, g: GraphDef) {
        // Подписи графика считаются от размера области, а не в абсолютных пикселях:
        // на большом экране машины и в маленьком предпросмотре на телефоне вид одинаковый.
        val unit = min(area.width(), area.height())
        if (unit < 32f) return
        val titleSize = max(11f * pxScale, unit * 0.07f)
        val subSize = titleSize * 0.72f
        var legendSize = max(10f * pxScale, unit * 0.055f)

        val left = area.left + area.width() * 0.06f
        val right = area.right - area.width() * 0.04f
        val top = area.top + titleSize * 2f
        val bottom = area.bottom - if (g.pids.size > 1) legendSize * 2.1f else legendSize * 0.8f
        if (right - left < 10f || bottom - top < 10f) return

        // окно времени
        val now = SystemClock.uptimeMillis()
        val windowMs = g.windowMin * 60_000L
        val cutoff = now - windowMs

        // собрать точки по pid
        val series = ArrayList<Pair<String, List<Pair<Long, Double>>>>()
        for (pid in g.pids) {
            val pts = NavData.history(pid).filter { it.first >= cutoff }
            series.add(pid to pts)
        }

        // Y-диапазон
        val allVals = series.flatMap { it.second.map { p -> p.second } }
        var yMin: Double
        var yMax: Double
        if (g.autoScaleY && allVals.isNotEmpty()) {
            yMin = allVals.minOrNull()!!
            yMax = allVals.maxOrNull()!!
            if (yMax - yMin < 1e-6) { yMin -= 1.0; yMax += 1.0 }
            val pad = (yMax - yMin) * 0.1
            yMin -= pad; yMax += pad
        } else {
            // из PidDef
            val defs = g.pids.mapNotNull { MazdaPids.byId(it) }
            yMin = defs.map { it.min }.minOrNull()?.toDouble() ?: 0.0
            yMax = defs.map { it.max }.maxOrNull()?.toDouble() ?: 100.0
            if (yMax <= yMin) { yMin = 0.0; yMax = 100.0 }
        }

        // сетка
        if (g.grid) {
            stroke.strokeWidth = 1f * pxScale
            stroke.color = Color.argb(60, 170, 185, 220)
            for (i in 0..4) {
                val yy = top + (bottom - top) * i / 4f
                c.drawLine(left, yy, right, yy, stroke)
            }
            for (i in 0..4) {
                val xx = left + (right - left) * i / 4f
                c.drawLine(xx, top, xx, bottom, stroke)
            }
        }
        // рамка
        stroke.strokeWidth = 1.5f * pxScale
        stroke.color = Color.argb(120, 170, 185, 220)
        c.drawRect(left, top, right, bottom, stroke)

        // линии
        for ((idx, pid) in g.pids.withIndex()) {
            val pts = series[idx].second
            if (pts.size < 2) continue
            val color = if (idx < g.colors.size) g.colors[idx] else 0xFF3AA6FF.toInt()
            stroke.color = color
            stroke.strokeWidth = g.thickness * pxScale
            stroke.strokeCap = Paint.Cap.ROUND
            var prevX = 0f; var prevY = 0f; var first = true
            for (p in pts) {
                val t = ((p.first - cutoff).toFloat() / windowMs).coerceIn(0f, 1f)
                val x = left + (right - left) * t
                val frac = ((p.second - yMin) / (yMax - yMin)).coerceIn(0.0, 1.0).toFloat()
                val y = bottom - (bottom - top) * frac
                if (!first) c.drawLine(prevX, prevY, x, y, stroke)
                prevX = x; prevY = y; first = false
            }
            stroke.strokeCap = Paint.Cap.BUTT
        }

        // заголовок и окно времени
        text.color = Color.WHITE
        text.textSize = titleSize
        text.textAlign = Paint.Align.LEFT
        c.drawText(g.name, left, area.top + titleSize * 1.25f, text)
        text.textSize = subSize
        text.color = Color.rgb(170, 185, 220)
        text.textAlign = Paint.Align.RIGHT
        c.drawText("${g.windowMin} мин", right, area.top + titleSize * 1.25f, text)
        text.textAlign = Paint.Align.CENTER

        // легенда (если >1): крупная, во всю ширину графика, с подгонкой по месту
        if (g.pids.size > 1) {
            val names = g.pids.map { pid ->
                val pd = MazdaPids.byId(pid)
                if (appContext != null && pd != null) pd.short(appContext!!) else pid
            }
            text.textSize = legendSize
            val dotR = legendSize * 0.32f
            val gapAfterDot = legendSize * 0.6f
            val itemGap = legendSize * 1.6f
            var totalW = 0f
            for (nm in names) totalW += dotR * 2f + gapAfterDot + text.measureText(nm) + itemGap
            val availW = right - left
            if (totalW > availW && totalW > 0f) {
                legendSize = max(9f * pxScale, legendSize * availW / totalW)
                text.textSize = legendSize
            }
            val r = legendSize * 0.32f
            val gap = legendSize * 0.6f
            val step = legendSize * 1.6f
            val ly = area.bottom - legendSize * 1.05f
            var lx = left
            text.textAlign = Paint.Align.LEFT
            for ((idx, nm) in names.withIndex()) {
                val color = if (idx < g.colors.size) g.colors[idx] else 0xFF3AA6FF.toInt()
                fill.color = color
                c.drawCircle(lx + r, ly - legendSize * 0.32f, r, fill)
                text.color = Color.rgb(200, 210, 240)
                c.drawText(nm, lx + r * 2f + gap, ly, text)
                lx += r * 2f + gap + text.measureText(nm) + step
            }
            text.textAlign = Paint.Align.CENTER
        }
    }

    /** Подписи прибора: показание, единицы и название. */
    private fun gaugeTexts(c: Canvas, cx: Float, cy: Float, r: Float, item: NavItem, valueText: String) {
        text.color = Color.WHITE
        text.textSize = r * 0.30f
        c.drawText(valueText, cx, cy + r * 0.11f, text)
        text.color = Color.rgb(170, 185, 220)
        text.textSize = r * 0.13f
        c.drawText(item.unit, cx, cy + r * 0.30f, text)
        text.textSize = r * 0.14f
        c.drawText(item.label, cx, cy + r * 0.86f, text)
    }
}
