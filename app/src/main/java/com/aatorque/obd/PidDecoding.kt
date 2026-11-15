package com.aatorque.obd

/**
 * Чистые функции разбора ответов адаптера: без Android и ресурсов, поэтому проверяются
 * юнит-тестами на JVM (app/src/test/java/com/aatorque/obd).
 */
object ElmParse {

    /** Байты ответа после префикса (например «410C» или «620434»); null, если ответа нет. */
    fun bytes(text: String?, prefix: String): IntArray? {
        if (text.isNullOrBlank()) return null
        val lines = text.uppercase().split(Regex("[\\r\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        // многострочный ответ ELM327 вида «0: 62 04 34 …», «1: …» — берём только байты после двоеточия
        val multi = lines.any { Regex("^[0-9A-F]\\s*:").containsMatchIn(it) }
        val sb = StringBuilder()
        for (l in lines) {
            if (multi) {
                Regex("^[0-9A-F]\\s*:([0-9A-F ]*)$").find(l)?.let { sb.append(it.groupValues[1]) }
            } else if (Regex("^[0-9A-F ]+$").matches(l)) {
                sb.append(l)
            }
        }
        val hex = sb.toString().replace(" ", "")
        val i = hex.indexOf(prefix.uppercase())
        if (i < 0) return null
        val rest = hex.substring(i + prefix.length)
        return IntArray(rest.length / 2) { rest.substring(it * 2, it * 2 + 2).toInt(16) }
    }

    /** Температуры трёх датчиков выхлопа из ответа «01 78» (4 пары байт, нулевой датчик отсутствует). */
    fun egtTemperatures(b: IntArray, offset: Int = 1): List<Double> =
        (0 until 4)
            .filter { i -> b.u(offset + 2 * i) != 0 || b.u(offset + 2 * i + 1) != 0 }
            .map { i -> (b.u(offset + 2 * i) * 256 + b.u(offset + 2 * i + 1)) / 10.0 - 40.0 }

    private fun IntArray.u(i: Int): Int = if (i < size) this[i] else 0
}

/**
 * Формулы датчиков Mazda SkyActiv-D, подтверждённые на автомобиле.
 * Все значения — из байтов ответа без префикса (см. [ElmParse.bytes]).
 */
object MazdaDecode {

    /** Знаковое 16-битное из двух байт (температуры АКПП и перепад давления бывают отрицательными). */
    fun s16(h: Int, l: Int): Int {
        val v = h * 256 + l
        return if (v > 32767) v - 65536 else v
    }

    /** Стандартные датчики режима 01: температура со смещением −40 °C. */
    fun at(c: Int): Double = c - 40.0

    /** Стандартные датчики режима 01: доля от 0 до 100 %. */
    fun percent(b: Int): Double = b * 100 / 255.0

    /** 01 0C — обороты, об/мин. */
    fun rpm(b: IntArray): Double = (b.u(0) * 256 + b.u(1)) / 4.0

    /** 01 23 — давление в рампе, МПа. */
    fun railPressure(b: IntArray): Double = (b.u(0) * 256 + b.u(1)) * 10 / 1000.0

    /** 01 10 — расход воздуха, г/с. */
    fun maf(b: IntArray): Double = (b.u(0) * 256 + b.u(1)) / 100.0

    /** 01 42 — напряжение бортсети, В. */
    fun voltage(b: IntArray): Double = (b.u(0) * 256 + b.u(1)) / 1000.0

    /** 22 042C (сажа, накопление) и 22 042D (сажа, образование), г/л. */
    fun soot(b: IntArray): Double = (b.u(0) * 256 + b.u(1)) * 100 / 65536.0

    /** 22 0432 — счётчик регенераций. */
    fun regenCounter(b: IntArray): Double = (b.u(0) * 256 + b.u(1)).toDouble()

    /** 22 0434 — пробег после последней регенерации, км (три байта). */
    fun kmSinceRegen(b: IntArray): Double =
        ((b.u(1) shl 16) + (b.u(2) shl 8) + b.u(3)) * 100 / 65536.0

    /** 7E1 22 1E1C — температура масла АКПП, °C. */
    fun transTemp(b: IntArray): Double = s16(b.u(0), b.u(1)) / 16.0

    /** 22 0380 — статус регенерации: 0 нет, 1/2 идёт (различие не подтверждено). */
    fun regenStatus(b: IntArray): Double = b.u(0).toDouble()

    /** 01 7A — перепад давления на сажевом фильтре, кПа; null, если блок сообщает «нет данных». */
    fun dpfPressure(b: IntArray): Double? =
        if ((b.u(0) and 1) != 0) s16(b.u(1), b.u(2)) / 100.0 else null

    /** 01 70 — наддув относительно атмосферы, бар; baro — давление с датчика 01 33, кПа. */
    fun boost(b: IntArray, baro: Double): Double? =
        if ((b.u(0) and 2) != 0) ((b.u(3) * 256 + b.u(4)) * 0.03125 - baro) / 100.0 else null

    private fun IntArray.u(i: Int): Int = if (i < size) this[i] else 0
}
