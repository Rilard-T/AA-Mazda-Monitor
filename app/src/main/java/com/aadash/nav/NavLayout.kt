package com.aadash.nav

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Элемент раскладки: прибор (GAUGE) или текстовое значение (INFO). */
data class NavItem(
    val id: String,
    val kind: String,
    val pid: String,
    val label: String,
    val unit: String,
    val min: Float,
    val max: Float,
    val scale: Double,
    val decimals: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val size: Float,
    /** Начало красной зоны (отсечка) в единицах шкалы; 0 или вне диапазона — зоны нет. */
    val red: Float = 0f,
    /** Вид границы виджета в редакторе: circle (по умолчанию для GAUGE) / square / none. */
    val border: String = ""
) {
    companion object {
        const val GAUGE = "gauge"
        const val INFO = "info"
        const val MEDIA = "media"
        const val GRAPH = "graph"
    }
}

/** График (одиночный или совмещённый): список pid, цвета, окно, толщина, сетка, автомасштаб. */
data class GraphDef(
    val id: String,
    val name: String,
    val pids: List<String>,
    val colors: List<Int>,
    val windowMin: Int = 5,
    val thickness: Float = 3f,
    val grid: Boolean = true,
    val autoScaleY: Boolean = true
)

/**
 * Уведомление о прожиге в строке шапки: там же, где «Выход» и кнопки экранов, не ниже их.
 * [x] — сдвиг по горизонтали (0 — к левому краю, 1 — к правому), [scale] — множитель
 * к базовой высоте 28 dp, он же масштабирует текст, отступы и скругление.
 */
data class RegenBanner(
    val enabled: Boolean = true,
    val placement: String = PLACEMENT_HEADER,
    val x: Float = 0f,
    val scale: Float = 1f,
    val fill: Int = 0xB4281E0A.toInt(),
    val text: Int = 0xFFFFE6B4.toInt(),
    val border: Int = 0xDCFFBE3C.toInt()
) {
    companion object {
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 2f
        const val PLACEMENT_HEADER = "header"
        const val PLACEMENT_FIRST_ROW = "firstRow"
        val DEFAULT = RegenBanner()

        fun legacyCorner(hasX: Boolean, corner: Int): Int = if (hasX) -1 else corner

        fun migrateX(hasX: Boolean, x: Double, oldCorner: Int): Float =
            if (hasX) x.toFloat().coerceIn(0f, 1f)
            else if (oldCorner == 1 || oldCorner == 3) 1f else 0f
    }
}

/** Набор элементов для одной формы области. */
data class NavForm(val items: List<NavItem>)

/** Раскладка: формы области, графики-экраны, параметры. */
data class NavLayout(
    val narrowAspect: Float,
    val forms: Map<String, NavForm>,
    val graphs: List<GraphDef> = emptyList(),
    val theme: String = "",
    val font: String = "",
    val ticks: Boolean = true,
    val debug: Boolean = false,
    val regen: RegenBanner = RegenBanner.DEFAULT,
    /** Множитель размера текста на приборах и графиках (для плохого зрения). */
    val textScale: Float = 1f
) {
    companion object {
        const val MIN_TEXT_SCALE = 0.7f
        const val MAX_TEXT_SCALE = 1.6f
        const val MIN_CIRCLE_SCALE = 0.3f
        const val MAX_CIRCLE_SCALE = 2.0f

        /**
         * Максимальный множитель круга для [item] при соотношении сторон [aspect],
         * при котором круг не выходит за границы области [0..1] по X и Y.
         */
        fun maxCircleSize(item: NavItem, aspect: Float): Float {
            if (aspect <= 0f || item.w <= 0f || item.h <= 0f) return MAX_CIRCLE_SCALE
            val cx = item.x + item.w / 2f
            val cy = item.y + item.h / 2f
            val maxR = minOf(
                aspect * minOf(cx, 1f - cx),
                minOf(cy, 1f - cy)
            ).coerceAtLeast(0f)
            val baseR = minOf(item.w * aspect, item.h) / 2f
            if (baseR <= 0f) return MAX_CIRCLE_SCALE
            return (maxR / baseR).coerceIn(MIN_CIRCLE_SCALE, MAX_CIRCLE_SCALE)
        }
    }

    fun form(key: String): NavForm =
        forms[key] ?: forms[NavLayoutStore.WIDE] ?: NavLayoutStore.defaultLayout().forms.getValue(NavLayoutStore.WIDE)
}

object NavLayoutStore {
    const val WIDE = "wide"
    const val NARROW = "narrow"
    private const val KEY = "layout_json"
    private const val KEY_AREA = "area_aspect"
    private const val KEY_AREA_WIDE = "area_aspect_wide"
    private const val KEY_AREA_NARROW = "area_aspect_narrow"

    @Volatile var layout: NavLayout = defaultLayout()
        private set

    @Volatile var carAspect: Float = 0f
        private set
    @Volatile var carAspectWide: Float = 0f
        private set
    @Volatile var carAspectNarrow: Float = 0f
        private set

    fun carAspectFor(narrowForm: Boolean): Float =
        if (narrowForm) carAspectNarrow else carAspectWide

    @Volatile var fastPids: List<String> = emptyList()
        private set
    @Volatile var slowPids: List<String> = emptyList()
        private set

    init { install(layout) }

    private fun install(l: NavLayout) {
        val fast = LinkedHashSet<String>()
        val slow = LinkedHashSet<String>()
        for (form in l.forms.values) {
            for (item in form.items) {
                if (item.kind == NavItem.GAUGE) fast.add(item.pid)
            }
        }
        // графики тоже опрашиваем (slow)
        for (g in l.graphs) for (p in g.pids) if (!fast.contains(p)) slow.add(p)
        for (form in l.forms.values) {
            for (item in form.items) {
                if (item.kind != NavItem.GAUGE && item.kind != NavItem.MEDIA && !fast.contains(item.pid)) slow.add(item.pid)
            }
        }
        fastPids = fast.toList()
        slowPids = slow.toList()
        layout = l
    }

    fun load(context: Context) {
        val prefs = try {
            context.applicationContext.getSharedPreferences(com.aadash.prefs.AppProfiles.navPrefsName(context), Context.MODE_PRIVATE)
        } catch (e: Exception) { null }
        val text = try { prefs?.getString(KEY, null) } catch (e: Exception) { null }
        val parsed = if (text == null) null else parse(text)
        val installed = parsed ?: defaultLayoutFor(context)
        install(installed)
        val wide = NavAspects.sanitize(try { prefs?.getFloat(KEY_AREA_WIDE, 0f) ?: 0f } catch (e: Exception) { 0f })
        val narrow = NavAspects.sanitize(try { prefs?.getFloat(KEY_AREA_NARROW, 0f) ?: 0f } catch (e: Exception) { 0f })
        val legacy = NavAspects.sanitize(try { prefs?.getFloat(KEY_AREA, 0f) ?: 0f } catch (e: Exception) { 0f })
        if (wide > 0f || narrow > 0f) {
            carAspectWide = wide
            carAspectNarrow = narrow
            carAspect = if (wide > 0f) wide else narrow
        } else {
            if (legacy > 0f && legacy < installed.narrowAspect) {
                carAspectNarrow = legacy
                carAspectWide = 0f
            } else {
                carAspectWide = legacy
                carAspectNarrow = 0f
            }
            carAspect = legacy
        }
    }

    fun reportArea(context: Context, aspect: Float) {
        val a = NavAspects.sanitize(aspect)
        if (a <= 0f) return
        val narrow = a < layout.narrowAspect
        val prev = if (narrow) carAspectNarrow else carAspectWide
        carAspect = a
        if (abs(a - prev) < 0.02f) return
        if (narrow) carAspectNarrow = a else carAspectWide = a
        try {
            context.applicationContext.getSharedPreferences(com.aadash.prefs.AppProfiles.navPrefsName(context), Context.MODE_PRIVATE)
                .edit()
                .putFloat(if (narrow) KEY_AREA_NARROW else KEY_AREA_WIDE, a)
                .putFloat(KEY_AREA, a)
                .apply()
        } catch (e: Exception) {}
    }

    fun save(context: Context, l: NavLayout) {
        try {
            context.applicationContext.getSharedPreferences(com.aadash.prefs.AppProfiles.navPrefsName(context), Context.MODE_PRIVATE)
                .edit().putString(KEY, toJson(l)).apply()
        } catch (e: Exception) {}
        install(l)
    }

    fun reset(context: Context) {
        try {
            context.applicationContext.getSharedPreferences(com.aadash.prefs.AppProfiles.navPrefsName(context), Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        } catch (e: Exception) {}
        install(defaultLayoutFor(context))
    }

    fun defaultLayoutFor(context: Context): NavLayout =
        if (com.aadash.prefs.AppProfiles.isUniversal(context)) universalLayout() else defaultLayout()

    fun toJson(l: NavLayout): String {
        val forms = JSONObject()
        for ((key, form) in l.forms) {
            val arr = JSONArray()
            for (item in form.items) {
                val o = JSONObject()
                o.put("id", item.id)
                o.put("kind", item.kind)
                o.put("pid", item.pid)
                o.put("label", item.label)
                o.put("unit", item.unit)
                o.put("min", item.min.toDouble())
                o.put("max", item.max.toDouble())
                o.put("scale", item.scale)
                o.put("dec", item.decimals)
                o.put("x", item.x.toDouble())
                o.put("y", item.y.toDouble())
                o.put("w", item.w.toDouble())
                o.put("h", item.h.toDouble())
                o.put("size", item.size.toDouble())
                o.put("red", item.red.toDouble())
                o.put("border", item.border)
                arr.put(o)
            }
            forms.put(key, JSONObject().put("items", arr))
        }
        val graphs = JSONArray()
        for (g in l.graphs) {
            val o = JSONObject()
            o.put("id", g.id)
            o.put("name", g.name)
            val pids = JSONArray(); for (p in g.pids) pids.put(p); o.put("pids", pids)
            val colors = JSONArray(); for (c in g.colors) colors.put(c); o.put("colors", colors)
            o.put("window", g.windowMin)
            o.put("thick", g.thickness.toDouble())
            o.put("grid", g.grid)
            o.put("auto", g.autoScaleY)
            graphs.put(o)
        }
        val regen = JSONObject()
            .put("on", l.regen.enabled)
            .put("place", l.regen.placement)
            .put("x", l.regen.x.coerceIn(0f, 1f).toDouble())
            .put("scale", l.regen.scale.toDouble())
            .put("fill", l.regen.fill)
            .put("text", l.regen.text)
            .put("border", l.regen.border)
        return JSONObject().put("v", 2).put("narrowAspect", l.narrowAspect.toDouble()).put("forms", forms)
            .put("graphs", graphs)
            .put("theme", l.theme).put("font", l.font).put("ticks", l.ticks).put("debug", l.debug)
            .put("textScale", l.textScale.toDouble())
            .put("regen", regen).toString()
    }

    fun parse(text: String): NavLayout? {
        try {
            val root = JSONObject(text)
            val formsJson = root.getJSONObject("forms")
            val forms = HashMap<String, NavForm>()
            val keys = formsJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = formsJson.getJSONObject(key).getJSONArray("items")
                val items = ArrayList<NavItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val item = NavItem(
                        id = o.getString("id"),
                        kind = o.getString("kind"),
                        pid = o.getString("pid"),
                        label = o.optString("label", ""),
                        unit = o.optString("unit", ""),
                        min = o.optDouble("min", 0.0).toFloat(),
                        max = o.optDouble("max", 100.0).toFloat(),
                        scale = o.optDouble("scale", 1.0),
                        decimals = o.optInt("dec", 0),
                        x = o.getDouble("x").toFloat(),
                        y = o.getDouble("y").toFloat(),
                        w = o.getDouble("w").toFloat(),
                        h = o.getDouble("h").toFloat(),
                        size = o.optDouble("size", 0.9).toFloat(),
                        red = o.optDouble("red", 0.0).toFloat(),
                        border = o.optString("border", "")
                    )
                    var fixed = if (item.pid.equals("0c", true) && item.max == 6f && item.red <= item.min) item.copy(max = 8f) else item
                    if (fixed.pid.equals("0c", true) && fixed.red <= fixed.min) fixed = fixed.copy(red = 6f)
                    if (fixed.kind == NavItem.INFO && fixed.pid.equals("0c", true)
                        && (fixed.unit.equals("x1000", true) || fixed.scale == 0.001)) {
                        fixed = fixed.copy(unit = "об/мин", scale = 1.0, decimals = 0)
                    }
                    if (fixed.max <= fixed.min || fixed.w <= 0f || fixed.h <= 0f) return null
                    items.add(fixed)
                }
                forms[key] = NavForm(items)
            }
            val wide = forms[WIDE]
            if (wide == null || wide.items.isEmpty()) return null

            val graphs = ArrayList<GraphDef>()
            if (root.has("graphs")) {
                val garr = root.getJSONArray("graphs")
                for (i in 0 until garr.length()) {
                    val o = garr.getJSONObject(i)
                    val pids = ArrayList<String>()
                    val pa = o.optJSONArray("pids")
                    if (pa != null) for (j in 0 until pa.length()) pids.add(pa.getString(j))
                    val colors = ArrayList<Int>()
                    val ca = o.optJSONArray("colors")
                    if (ca != null) for (j in 0 until ca.length()) colors.add(ca.getInt(j))
                    if (colors.size < pids.size) {
                        val palette = intArrayOf(0xFF3AA6FF.toInt(), 0xFFFFBE3C.toInt(), 0xFF7CFF6B.toInt(), 0xFFFF5A4A.toInt())
                        while (colors.size < pids.size) colors.add(palette[colors.size % palette.size])
                    }
                    graphs.add(GraphDef(
                        id = o.optString("id", "g$i"),
                        name = o.optString("name", "График ${i + 1}"),
                        pids = pids,
                        colors = colors,
                        windowMin = o.optInt("window", 5),
                        thickness = o.optDouble("thick", 3.0).toFloat(),
                        grid = o.optBoolean("grid", true),
                        autoScaleY = o.optBoolean("auto", true)
                    ))
                }
            }

            val regenJson = root.optJSONObject("regen")
            // Старые раскладки хранили угол (0..3): слева — по левому краю, справа — по правому.
            val hasX = regenJson?.has("x") == true
            val oldCorner = RegenBanner.legacyCorner(hasX, regenJson?.optInt("corner", 0) ?: 0)
            val regen = if (regenJson == null) RegenBanner.DEFAULT else RegenBanner(
                enabled = regenJson.optBoolean("on", true),
                placement = regenJson.optString("place", RegenBanner.PLACEMENT_HEADER),
                x = RegenBanner.migrateX(hasX, regenJson.optDouble("x", 0.0), oldCorner),
                scale = regenJson.optDouble("scale", 1.0).toFloat()
                    .coerceIn(RegenBanner.MIN_SCALE, RegenBanner.MAX_SCALE),
                fill = regenJson.optInt("fill", RegenBanner.DEFAULT.fill),
                text = regenJson.optInt("text", RegenBanner.DEFAULT.text),
                border = regenJson.optInt("border", RegenBanner.DEFAULT.border)
            )

            return NavLayout(
                root.optDouble("narrowAspect", 1.15).toFloat(), forms, graphs,
                root.optString("theme", ""), root.optString("font", ""),
                root.optBoolean("ticks", true), root.optBoolean("debug", false), regen,
                root.optDouble("textScale", 1.0).toFloat()
                    .coerceIn(NavLayout.MIN_TEXT_SCALE, NavLayout.MAX_TEXT_SCALE)
            )
        } catch (e: Exception) {
            return null
        }
    }

    fun defaultLayout(): NavLayout {
        val footer = 0.16f
        val gh = 1f - footer
        val third = 1f / 3f
        val fourth = 0.25f

        fun gauge(
            id: String, pid: String, label: String, unit: String, min: Float, max: Float, scale: Double,
            x: Float, y: Float, w: Float, h: Float, size: Float, red: Float = 0f
        ) = NavItem(id, NavItem.GAUGE, pid, label, unit, min, max, scale, 0, x, y, w, h, size, red, "circle")

        fun info(id: String, pid: String, label: String, unit: String, decimals: Int, x: Float, y: Float, w: Float, h: Float) =
            NavItem(id, NavItem.INFO, pid, label, unit, 0f, 1f, 1.0, decimals, x, y, w, h, 1f, 0f, "square")

        val wide = NavForm(
            listOf(
                gauge("g0", "0d", "Скорость", "км/ч", 0f, 240f, 1.0, 0f, 0f, third, gh, 0.95f),
                gauge("g1", "0c", "Обороты", "x1000", 0f, 8f, 0.001, third, 0f, third, gh, 1.0f, 6f),
                gauge("g2", "05", "Темп. ОЖ", "°C", 40f, 120f, 1.0, 2f * third, 0f, third, gh, 0.95f),
                info("i0", "ff9010", "DPF", "%", 0, 0f, gh, fourth, footer),
                info("i1", "ff9003", "Сажа", "г/л", 2, fourth, gh, fourth, footer),
                info("i2", "ff9005", "АКПП", "°C", 0, 2f * fourth, gh, fourth, footer),
                info("i3", "ff9002", "После прожига", "км", 0, 3f * fourth, gh, fourth, footer)
            )
        )
        val narrowRow = 1f / 7f
        fun narrowInfo(id: String, pid: String, label: String, unit: String, decimals: Int, row: Int, scale: Double = 1.0) =
            NavItem(id, NavItem.INFO, pid, label, unit, 0f, 1f, scale, decimals, 0f, row * narrowRow, 1f, narrowRow, 1f, 0f, "square")
        val narrow = NavForm(
            listOf(
                narrowInfo("i0", "0d", "Скорость", "км/ч", 0, 0),
                narrowInfo("i1", "0c", "Обороты", "об/мин", 0, 1),
                narrowInfo("i2", "05", "Темп. ОЖ", "°C", 0, 2),
                narrowInfo("i3", "ff9010", "DPF", "%", 0, 3),
                narrowInfo("i4", "ff9003", "Сажа", "г/л", 2, 4),
                narrowInfo("i5", "ff9005", "АКПП", "°C", 0, 5),
                narrowInfo("i6", "ff9002", "После прожига", "км", 0, 6)
            )
        )
        return NavLayout(1.15f, mapOf(WIDE to wide, NARROW to narrow), emptyList(), "", "")
    }

    /** Шаблон «Трасса»: два крупных прибора (обороты и скорость), трек и основные температуры/DPF. */
    fun presetHighway(): NavLayout {
        val footer = 0.18f
        val gh = 1f - footer
        fun gauge(
            id: String, pid: String, label: String, unit: String, min: Float, max: Float, scale: Double,
            x: Float, y: Float, w: Float, h: Float, size: Float, red: Float = 0f
        ) = NavItem(id, NavItem.GAUGE, pid, label, unit, min, max, scale, 0, x, y, w, h, size, red, "circle")
        fun info(id: String, pid: String, label: String, unit: String, decimals: Int, x: Float, y: Float, w: Float, h: Float, red: Float = 0f) =
            NavItem(id, NavItem.INFO, pid, label, unit, 0f, 1f, 1.0, decimals, x, y, w, h, 1f, red, "square")

        val wide = NavForm(
            listOf(
                gauge("g0", "0c", "Обороты", "x1000", 0f, 8f, 0.001, 0f, 0f, 0.5f, gh, 0.98f, 6f),
                gauge("g1", "0d", "Скорость", "км/ч", 0f, 240f, 1.0, 0.5f, 0f, 0.5f, gh, 0.98f),
                NavItem("m0", NavItem.MEDIA, "media_title", "Трек", "", 0f, 1f, 1.0, 0, 0f, gh, 0.5f, footer, 1f, 0f, "square"),
                info("i0", "05", "Темп. ОЖ", "°C", 0, 0.5f, gh, 0.25f, footer, 105f),
                info("i1", "ff9010", "DPF", "%", 0, 0.75f, gh, 0.25f, footer, 95f)
            )
        )
        val narrow = NavForm(
            listOf(
                gauge("g0", "0d", "Скорость", "км/ч", 0f, 240f, 1.0, 0f, 0f, 1f, 0.52f, 0.95f),
                NavItem("m0", NavItem.MEDIA, "media_title", "Трек", "", 0f, 1f, 1.0, 0, 0f, 0.52f, 1f, 0.16f, 1f, 0f, "square"),
                info("i0", "05", "Темп. ОЖ", "°C", 0, 0f, 0.68f, 0.5f, 0.32f, 105f),
                info("i1", "ff9010", "DPF", "%", 0, 0.5f, 0.68f, 0.5f, 0.32f, 95f)
            )
        )
        return NavLayout(1.15f, mapOf(WIDE to wide, NARROW to narrow), emptyList(), "", "")
    }

    /** Шаблон «Минимализм»: крупный спидометр по центру и две плитки (ОЖ и DPF). */
    fun presetMinimal(): NavLayout {
        val wide = NavForm(
            listOf(
                NavItem("g0", NavItem.GAUGE, "0d", "Скорость", "км/ч", 0f, 240f, 1.0, 0, 0.2f, 0f, 0.6f, 0.80f, 1f, 0f, "circle"),
                NavItem("i0", NavItem.INFO, "05", "Темп. ОЖ", "°C", 0f, 1f, 1.0, 0, 0f, 0.80f, 0.5f, 0.20f, 1f, 105f, "square"),
                NavItem("i1", NavItem.INFO, "ff9010", "DPF", "%", 0f, 1f, 1.0, 0, 0.5f, 0.80f, 0.5f, 0.20f, 1f, 95f, "square")
            )
        )
        val narrow = NavForm(
            listOf(
                NavItem("g0", NavItem.GAUGE, "0d", "Скорость", "км/ч", 0f, 240f, 1.0, 0, 0f, 0f, 1f, 0.64f, 0.95f, 0f, "circle"),
                NavItem("i0", NavItem.INFO, "05", "Темп. ОЖ", "°C", 0f, 1f, 1.0, 0, 0f, 0.64f, 0.5f, 0.36f, 1f, 105f, "square"),
                NavItem("i1", NavItem.INFO, "ff9010", "DPF", "%", 0f, 1f, 1.0, 0, 0.5f, 0.64f, 0.5f, 0.36f, 1f, 95f, "square")
            )
        )
        return NavLayout(1.15f, mapOf(WIDE to wide, NARROW to narrow), emptyList(), "", "")
    }

    /** Универсальный шаблон OBD-II (стандартные PID без привязки к Mazda/DPF). */
    fun universalLayout(): NavLayout {
        val footer = 0.16f
        val gh = 1f - footer
        val third = 1f / 3f
        val fourth = 0.25f

        fun gauge(
            id: String, pid: String, label: String, unit: String, min: Float, max: Float, scale: Double,
            x: Float, y: Float, w: Float, h: Float, size: Float, red: Float = 0f
        ) = NavItem(id, NavItem.GAUGE, pid, label, unit, min, max, scale, 0, x, y, w, h, size, red, "circle")

        fun info(id: String, pid: String, label: String, unit: String, decimals: Int, x: Float, y: Float, w: Float, h: Float, red: Float = 0f) =
            NavItem(id, NavItem.INFO, pid, label, unit, 0f, 1f, 1.0, decimals, x, y, w, h, 1f, red, "square")

        val wide = NavForm(
            listOf(
                gauge("g0", "0d", "Скорость", "км/ч", 0f, 240f, 1.0, 0f, 0f, third, gh, 0.95f),
                gauge("g1", "0c", "Обороты", "x1000", 0f, 8f, 0.001, third, 0f, third, gh, 1.0f, 6f),
                gauge("g2", "05", "Темп. ОЖ", "°C", 40f, 120f, 1.0, 2f * third, 0f, third, gh, 0.95f, 105f),
                info("i0", "04", "Нагрузка", "%", 0, 0f, gh, fourth, footer),
                info("i1", "0b", "Впуск MAP", "кПа", 0, fourth, gh, fourth, footer),
                info("i2", "0f", "Воздух", "°C", 0, 2f * fourth, gh, fourth, footer),
                info("i3", "42", "Бортсеть", "В", 1, 3f * fourth, gh, fourth, footer)
            )
        )
        val narrowRow = 1f / 7f
        fun narrowInfo(id: String, pid: String, label: String, unit: String, decimals: Int, row: Int, red: Float = 0f) =
            NavItem(id, NavItem.INFO, pid, label, unit, 0f, 1f, 1.0, decimals, 0f, row * narrowRow, 1f, narrowRow, 1f, red, "square")
        val narrow = NavForm(
            listOf(
                narrowInfo("i0", "0d", "Скорость", "км/ч", 0, 0),
                narrowInfo("i1", "0c", "Обороты", "об/мин", 0, 1),
                narrowInfo("i2", "05", "Темп. ОЖ", "°C", 0, 2, 105f),
                narrowInfo("i3", "04", "Нагрузка", "%", 0, 3),
                narrowInfo("i4", "0b", "Впуск MAP", "кПа", 0, 4),
                narrowInfo("i5", "0f", "Воздух", "°C", 0, 5),
                narrowInfo("i6", "42", "Бортсеть", "В", 1, 6)
            )
        )
        return NavLayout(1.15f, mapOf(WIDE to wide, NARROW to narrow), emptyList(), "", "")
    }
}
