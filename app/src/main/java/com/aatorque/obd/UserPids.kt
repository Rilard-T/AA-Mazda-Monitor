package com.aatorque.obd

import android.content.Context
import android.content.SharedPreferences

/**
 * Датчик, добавленный пользователем вручную или импортом Torque CSV.
 * После сохранения он попадает в общий каталог [MazdaPids.all], поэтому его видно
 * в проверке датчиков, в списке PID дашборда и в редакторе раскладки AA.
 */
data class UserPid(
    val name: String,
    /** Короткое имя для приборов; пустое — берётся [name]. */
    val shortName: String,
    /** Режим и PID одним числом в hex, например «22042C» или «010C». */
    val modeAndPid: String,
    /** Формула в стиле Torque: переменные A..P — байты ответа. */
    val equation: String,
    val min: Double,
    val max: Double,
    val unit: String,
    /** Блок, которому адресован запрос: 7E0 — двигатель, 7E1 — АКПП. */
    val header: String,
) {
    /** Идентификатор в каталоге: префикс «u» не даёт пересечься со встроенными датчиками. */
    val key: String get() = "u" + modeAndPid.lowercase()

    fun toPidDef(): PidDef = PidDef(
        id = key,
        min = min,
        max = max,
        header = header,
        request = modeAndPid,
        decode = { bytes -> UserPidFormula.evaluate(equation, bytes) },
        label = name,
        shortLabel = if (shortName.isNotEmpty()) shortName else name,
        unitLabel = unit,
    )
}

/**
 * Хранилище пользовательских датчиков. Отдельный файл настроек, чтобы не смешиваться
 * с «obd_bridge» и переживать сброс настроек подключения.
 */
object UserPids {

    private const val KEY_LIST = "list"

    /** Больше датчиков не нужно: каждый из них — отдельный запрос к блоку. */
    const val MAX = 64

    @Volatile var revision: Int = 0
        private set

    private var prefs: SharedPreferences? = null
    private var items: List<UserPid> = emptyList()
    private var defCache: List<PidDef> = emptyList()

    /** Загружает список при старте приложения: каталог PID должен быть готов до первого опроса. */
    fun load(context: Context) {
        val p = context.getSharedPreferences(com.aatorque.prefs.AppProfiles.userPidsPrefsName(context), Context.MODE_PRIVATE)
        prefs = p
        items = UserPidCodec.decode(p.getString(KEY_LIST, null))
        rebuild()
    }

    fun list(): List<UserPid> = items

    fun defs(): List<PidDef> = defCache

    fun byKey(key: String): UserPid? = items.firstOrNull { it.key.equals(key, ignoreCase = true) }

    /**
     * Добавляет датчик или заменяет прежний с тем же «режим+PID»:
     * повторный импорт CSV обновляет формулы, а не плодит дубликаты.
     */
    fun put(context: Context, pid: UserPid): Boolean {
        val p = prefs ?: context.getSharedPreferences(com.aatorque.prefs.AppProfiles.userPidsPrefsName(context), Context.MODE_PRIVATE).also { prefs = it }
        val old = items.firstOrNull { it.key.equals(pid.key, ignoreCase = true) }
        if (old == pid) return false
        items = (items.filterNot { it.key.equals(pid.key, ignoreCase = true) } + pid).take(MAX)
        p.edit().putString(KEY_LIST, UserPidCodec.encode(items)).apply()
        rebuild()
        return true
    }

    /** Импорт CSV: датчики добавляются пачкой, уже известные обновляются. */
    fun putAll(context: Context, pids: List<UserPid>): Int {
        if (pids.isEmpty()) return 0
        val p = prefs ?: context.getSharedPreferences(com.aatorque.prefs.AppProfiles.userPidsPrefsName(context), Context.MODE_PRIVATE).also { prefs = it }
        val byKey = LinkedHashMap<String, UserPid>()
        items.forEach { byKey[it.key] = it }
        pids.forEach { byKey[it.key] = it }
        items = byKey.values.toList().take(MAX)
        p.edit().putString(KEY_LIST, UserPidCodec.encode(items)).apply()
        rebuild()
        return pids.size
    }

    fun remove(context: Context, key: String) {
        val p = prefs ?: context.getSharedPreferences(com.aatorque.prefs.AppProfiles.userPidsPrefsName(context), Context.MODE_PRIVATE).also { prefs = it }
        val next = items.filterNot { it.key.equals(key, ignoreCase = true) }
        if (next.size == items.size) return
        items = next
        p.edit().putString(KEY_LIST, UserPidCodec.encode(items)).apply()
        rebuild()
    }

    private fun rebuild() {
        defCache = items.map { it.toPidDef() }
        revision++
        // Список PID кэширует StatsService для экранов дашборда — после правки он устарел.
        com.aatorque.stats.StatsService.pidsStale = true
    }
}

/**
 * Текстовый формат хранения: поля через табуляцию, служебные символы экранированы.
 * Специально без JSON и Room: список маленький, а так его можно проверить юнит-тестами.
 */
internal object UserPidCodec {

    fun encode(list: List<UserPid>): String = list.joinToString("\n") { pid ->
        listOf(
            pid.name,
            pid.shortName,
            pid.modeAndPid,
            pid.equation,
            format(pid.min),
            format(pid.max),
            pid.unit,
            pid.header,
        ).joinToString("\t") { escape(it) }
    }

    fun decode(text: String?): List<UserPid> {
        if (text.isNullOrBlank()) return emptyList()
        val out = ArrayList<UserPid>()
        for (line in text.split('\n')) {
            if (line.isBlank()) continue
            val f = line.split('\t').map { unescape(it) }
            if (f.size < 8) continue
            val pid = UserPid(
                name = f[0].trim(),
                shortName = f[1].trim(),
                modeAndPid = normalizePid(f[2]) ?: continue,
                equation = f[3].trim(),
                min = f[4].toDoubleOrNull() ?: 0.0,
                max = f[5].toDoubleOrNull() ?: 100.0,
                unit = f[6].trim(),
                header = normalizeHeader(f[7]),
            )
            if (pid.name.isEmpty() || pid.equation.isEmpty()) continue
            if (out.any { it.key.equals(pid.key, ignoreCase = true) }) continue
            out.add(pid)
            if (out.size >= UserPids.MAX) break
        }
        return out
    }

    /** «22 04 2c» и «0x22042c» приводятся к «22042C»; меньше четырёх hex-цифр — не PID. */
    fun normalizePid(raw: String): String? {
        // Torque экспортирует PID как «0x224027» — снимаем префикс 0x до чистки.
        val stripped = raw.trim().removePrefix("0x").removePrefix("0X")
        val hex = stripped.uppercase().replace(Regex("[^0-9A-F]"), "")
        if (hex.length < 4 || hex.length % 2 != 0) return null
        return hex
    }

    /** Заголовок блока: три hex-цифры; пусто — двигатель 7E0. */
    fun normalizeHeader(raw: String): String {
        val hex = raw.uppercase().replace(Regex("[^0-9A-F]"), "")
        return if (hex.length == 3) hex else DEFAULT_HEADER
    }

    private fun format(v: Double): String = String.format(java.util.Locale.US, "%.4f", v)

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    '\\' -> sb.append('\\')
                    else -> sb.append(c).append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private const val DEFAULT_HEADER = "7E0"
}
