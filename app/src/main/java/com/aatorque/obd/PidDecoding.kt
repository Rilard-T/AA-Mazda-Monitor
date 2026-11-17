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
        // Обычный путь не нашёл ответ — пробуем собрать его из сырых кадров ISO-TP.
        if (i < 0) return isoTp(text, prefix)
        val rest = hex.substring(i + prefix.length)
        return IntArray(rest.length / 2) { rest.substring(it * 2, it * 2 + 2).toInt(16) }
    }

    /**
     * Резервный разбор сырых кадров: часть адаптеров не убирает заголовок блока и служебные
     * байты ISO-TP, и данные приходят как «7E8 10 0A 62 1E» / «7E8 21 1C 12 34».
     * Здесь кадры собираются в один ответ: одиночный (0x0L), первый (0x1L LL) и продолжения (0x2L).
     * Нужен только тогда, когда прямой поиск префикса не нашёл ответ.
     */
    private fun isoTp(text: String, prefix: String): IntArray? {
        val payload = isoTpPayload(text) ?: return null
        val hex = buildString { payload.forEach { append("%02X".format(it)) } }
        val i = hex.indexOf(prefix.uppercase())
        if (i < 0) return null
        val rest = hex.substring(i + prefix.length)
        return IntArray(rest.length / 2) { rest.substring(it * 2, it * 2 + 2).toInt(16) }
    }

    private fun isoTpPayload(text: String): IntArray? {
        val frames = ArrayList<IntArray>()
        for (raw in text.uppercase().split(Regex("[\\r\\n]+"))) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            line = line.replace(Regex("^[0-9A-F]\\s*:"), "")       // «0: …» — номер строки ELM327
            line = line.replace(Regex("^[0-9A-F]{3}\\s+"), "")    // «7E8 …» — заголовок блока
            if (!Regex("^[0-9A-F ]+$").matches(line)) continue
            val clean = line.replace(" ", "")
            if (clean.length < 2) continue
            frames.add(IntArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16) })
        }
        val first = frames.firstOrNull() ?: return null
        val out = ArrayList<Int>()
        var expected = -1
        var index = 0
        when (first[0] shr 4) {
            0 -> {                                            // одиночный кадр: длина в младшей цифре
                val len = first[0] and 0x0F
                for (j in 1..len) if (j < first.size) out.add(first[j])
                return out.takeIf { it.isNotEmpty() }?.toIntArray()
            }
            1 -> {                                            // первый кадр длинного ответа
                if (first.size < 2) return null
                expected = ((first[0] and 0x0F) shl 8) or first[1]
                for (j in 2 until first.size) out.add(first[j])
                index = 1
            }
            else -> return null
        }
        val target = if (expected > 0) expected else Int.MAX_VALUE
        while (index < frames.size && out.size < target) {
            val f = frames[index++]
            if (f.isEmpty() || (f[0] shr 4) != 2) break          // не продолжение — собирать нечего
            for (j in 1 until f.size) out.add(f[j])
        }
        if (out.isEmpty()) return null
        if (expected in 1..out.size) return out.subList(0, expected).toIntArray()
        return out.toIntArray()
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

    /** 22 F47A — перепад давления на сажевом фильтре по данным блока, кПа (знаковый старший байт). */
    fun dpfDiffPressure(b: IntArray): Double = s16(b.u(1), b.u(2)) / 100.0

    /** 22 F469, байт C — положение клапана EGR A, %. */
    fun egrA(b: IntArray): Double = percent(b.u(2))

    /** 22 F469, байт F — положение клапана EGR B, %. */
    fun egrB(b: IntArray): Double = percent(b.u(5))

    /** 22 F411, байт A — положение заслонки впуска, %. */
    fun intakeShutter(b: IntArray): Double = percent(b.u(0))

    /** 22 DD01 — общий пробег по счётчику блока, км (три байта). */
    fun totalDistance(b: IntArray): Double = ((b.u(0) shl 16) + (b.u(1) shl 8) + b.u(2)).toDouble()

    /** 22 4028, байт A — оценка заряда АКБ, %. */
    fun batterySoc(b: IntArray): Double = percent(b.u(0))

    private fun IntArray.u(i: Int): Int = if (i < size) this[i] else 0
}
