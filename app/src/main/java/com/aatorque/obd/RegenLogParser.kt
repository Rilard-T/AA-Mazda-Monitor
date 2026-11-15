package com.aatorque.obd

/**
 * Чистые функции разбора и формирования текстового журнала прожигов.
 * Не зависят от Android, поэтому проверяются юнит-тестами на JVM.
 */
object RegenLogParser {

    data class Parsed(val entries: List<RegenEntry>, val intervals: List<Double>)

    /**
     * Разбирает текст в формате [RegenTracker.exportText].
     * Неверные строки пропускаются.
     */
    fun parse(text: String): Parsed {
        val entries = ArrayList<RegenEntry>()
        val ivs = ArrayList<Double>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val p = line.split(";")
            when (p.getOrNull(0)) {
                "L" -> {
                    val t = p.getOrNull(1)?.toLongOrNull() ?: continue
                    val s = p.getOrNull(2)?.toDoubleOrNull() ?: continue
                    val km = p.getOrNull(3)?.toDoubleOrNull()
                    val st = p.getOrNull(4)?.toIntOrNull() ?: RegenState.BY_COUNTER
                    if (t <= 0 || s < 0 || s > 30 || st !in 0..3) continue
                    entries.add(RegenEntry(t, s, km, st))
                }
                "I" -> p.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { ivs.add(it) }
            }
        }
        return Parsed(entries, ivs)
    }

    /**
     * Формирует текстовый журнал из записей и интервалов.
     */
    fun format(log: List<RegenEntry>, intervals: List<Double>): String = buildString {
        appendLine("# AA Mazda Monitor: журнал прожигов")
        appendLine("# L;время_мс;сажа_г/л;пробег_км;состояние (0 идёт, 1 завершён, 2 прерван, 3 по счётчику)")
        appendLine("# I;интервал_км")
        for (e in log) appendLine("L;${e.time};${e.soot};${e.km ?: ""};${e.state}")
        for (i in intervals) appendLine("I;$i")
    }
}
