package com.aatorque.nav

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Элемент раскладки: прибор (GAUGE) или текстовое значение (INFO).
 * Положение и размер (x, y, w, h) заданы долями видимой области (0..1), а не пикселями,
 * поэтому раскладка не привязана к размеру и плотности конкретного экрана.
 * size - доля вписанного в ячейку круга для прибора.
 */
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
    val red: Float = 0f
) {
    companion object {
        const val GAUGE = "gauge"
        const val INFO = "info"
        const val MEDIA = "media"
    }
}

/** Набор элементов для одной формы области. */
data class NavForm(val items: List<NavItem>)

/** Раскладка: формы области ("wide" - широкая, "narrow" - узкая колонка) и порог узкой формы (ширина < высота * narrowAspect). */
data class NavLayout(
    val narrowAspect: Float,
    val forms: Map<String, NavForm>,
    val theme: String = "",
    val ticks: Boolean = true,
    val debug: Boolean = false
) {
    fun form(key: String): NavForm =
        forms[key] ?: forms[NavLayoutStore.WIDE] ?: NavLayoutStore.defaultLayout().forms.getValue(NavLayoutStore.WIDE)
}

/** Хранение раскладки: в памяти для отрисовки, в настройках (JSON) между запусками. */
object NavLayoutStore {
    const val WIDE = "wide"
    const val NARROW = "narrow"
    private const val PREFS = "nav_layout"
    private const val KEY = "layout_json"
    private const val KEY_AREA = "area_aspect"

    @Volatile var layout: NavLayout = defaultLayout()
        private set

    /** Последняя пропорция области, которую сообщил Android Auto (ширина / высота); 0 - в машине ещё не запускались. */
    @Volatile var carAspect: Float = 0f
        private set

    /** Параметры, которые читаются часто (приборы) и редко (значения). */
    @Volatile var fastPids: List<String> = emptyList()
        private set
    @Volatile var slowPids: List<String> = emptyList()
        private set

    init {
        install(layout)
    }

    private fun install(l: NavLayout) {
        val fast = LinkedHashSet<String>()
        val slow = LinkedHashSet<String>()
        for (form in l.forms.values) {
            for (item in form.items) {
                if (item.kind == NavItem.GAUGE) fast.add(item.pid)
            }
        }
        for (form in l.forms.values) {
            for (item in form.items) {
                if (item.kind != NavItem.GAUGE && item.kind != NavItem.MEDIA && !fast.contains(item.pid)) slow.add(item.pid)
            }
        }
        fastPids = fast.toList()
        slowPids = slow.toList()
        layout = l
    }

    /** Загрузить раскладку из настроек; при отсутствии или ошибке - стандартная. */
    fun load(context: Context) {
        val text = try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        } catch (e: Exception) {
            null
        }
        val parsed = if (text == null) null else parse(text)
        install(parsed ?: defaultLayout())
        val area = try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_AREA, 0f)
        } catch (e: Exception) {
            0f
        }
        carAspect = NavAspects.sanitize(area)
    }

    /**
     * Запомнить пропорцию видимой области из машины: редактор на телефоне показывает предпросмотр
     * в тех же пропорциях. В настройки пишется только при заметном изменении, не каждый кадр.
     */
    fun reportArea(context: Context, aspect: Float) {
        val a = NavAspects.sanitize(aspect)
        if (a <= 0f) return
        if (abs(a - carAspect) < 0.02f) return
        carAspect = a
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_AREA, a).apply()
        } catch (e: Exception) {
        }
    }

    /** Сохранить раскладку и сразу применить. */
    fun save(context: Context, l: NavLayout) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, toJson(l)).apply()
        } catch (e: Exception) {
        }
        install(l)
    }

    /** Вернуть стандартную раскладку. */
    fun reset(context: Context) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        } catch (e: Exception) {
        }
        install(defaultLayout())
    }

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
                arr.put(o)
            }
            forms.put(key, JSONObject().put("items", arr))
        }
        return JSONObject().put("v", 1).put("narrowAspect", l.narrowAspect.toDouble()).put("forms", forms)
            .put("theme", l.theme).put("ticks", l.ticks).put("debug", l.debug).toString()
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
                        red = o.optDouble("red", 0.0).toFloat()
                    )
                    // сохранённые ранее раскладки: шкала оборотов была 0..6, а на циферблате темы 0..8
                    var fixed = if (item.pid.equals("0c", true) && item.max == 6f) item.copy(max = 8f) else item
                    // у оборотов по умолчанию красная зона с 6000: выше отсечки
                    if (fixed.pid.equals("0c", true) && fixed.red <= fixed.min) fixed = fixed.copy(red = 6f)
                    if (fixed.max <= fixed.min || fixed.w <= 0f || fixed.h <= 0f) return null
                    items.add(fixed)
                }
                forms[key] = NavForm(items)
            }
            val wide = forms[WIDE]
            if (wide == null || wide.items.isEmpty()) return null
            return NavLayout(
                root.optDouble("narrowAspect", 1.15).toFloat(), forms,
                root.optString("theme", ""), root.optBoolean("ticks", true), root.optBoolean("debug", false)
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Стандартная раскладка. Широкая область: три прибора в ряд и снизу четыре значения.
     * Узкая колонка: приборы столбиком слева, значения списком справа.
     */
    fun defaultLayout(): NavLayout {
        val footer = 0.16f
        val gh = 1f - footer
        val third = 1f / 3f
        val fourth = 0.25f
        val cw = 0.45f

        fun gauge(
            id: String, pid: String, label: String, unit: String, min: Float, max: Float, scale: Double,
            x: Float, y: Float, w: Float, h: Float, size: Float, red: Float = 0f
        ) = NavItem(id, NavItem.GAUGE, pid, label, unit, min, max, scale, 0, x, y, w, h, size, red)

        fun info(id: String, pid: String, label: String, unit: String, decimals: Int, x: Float, y: Float, w: Float, h: Float) =
            NavItem(id, NavItem.INFO, pid, label, unit, 0f, 1f, 1.0, decimals, x, y, w, h, 1f)

        val wide = NavForm(
            listOf(
                gauge("g0", "0d", "Скорость", "км/ч", 0f, 240f, 1.0, 0f, 0f, third, gh, 0.765f),
                gauge("g1", "0c", "Обороты", "x1000", 0f, 8f, 0.001, third, 0f, third, gh, 0.9f, 6f),
                gauge("g2", "05", "Темп. ОЖ", "°C", 40f, 120f, 1.0, 2f * third, 0f, third, gh, 0.765f),
                info("i0", "ff9010", "DPF", "%", 0, 0f, gh, fourth, footer),
                info("i1", "ff9003", "Сажа", "г/л", 2, fourth, gh, fourth, footer),
                info("i2", "ff9005", "АКПП", "°C", 0, 2f * fourth, gh, fourth, footer),
                info("i3", "ff9002", "После прожига", "км", 0, 3f * fourth, gh, fourth, footer)
            )
        )
        val narrow = NavForm(
            listOf(
                gauge("g0", "0d", "Скорость", "км/ч", 0f, 240f, 1.0, 0f, 0f, cw, third, 0.765f),
                gauge("g1", "0c", "Обороты", "x1000", 0f, 8f, 0.001, 0f, third, cw, third, 0.9f, 6f),
                gauge("g2", "05", "Темп. ОЖ", "°C", 40f, 120f, 1.0, 0f, 2f * third, cw, third, 0.765f),
                info("i0", "ff9010", "DPF", "%", 0, cw, 0f, 1f - cw, fourth),
                info("i1", "ff9003", "Сажа", "г/л", 2, cw, fourth, 1f - cw, fourth),
                info("i2", "ff9005", "АКПП", "°C", 0, cw, 2f * fourth, 1f - cw, fourth),
                info("i3", "ff9002", "После прожига", "км", 0, cw, 3f * fourth, 1f - cw, fourth)
            )
        )
        return NavLayout(1.15f, mapOf(WIDE to wide, NARROW to narrow))
    }
}
