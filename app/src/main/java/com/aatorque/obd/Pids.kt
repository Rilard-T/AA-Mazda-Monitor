package com.aatorque.obd

import java.util.Locale

typealias Decoder = (IntArray) -> Double?

/** Описание параметра. Либо запрос к блоку (request), либо расчётный (virtual). */
data class PidDef(
    val id: String,             // hex-подобный идентификатор для интерфейса в стиле Torque
    val name: String,           // без запятых: они разделители в строке информации
    val shortName: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val header: String = "7E0", // 7E0 — двигатель, 7E1 — АКПП
    val request: String? = null,// "010C" (режим 01) или "220434" (режим 22)
    val slow: Boolean = false,  // медленные параметры кэшируются дольше
    val decode: Decoder? = null,
    val virtual: ((ObdEngine) -> Double?)? = null,
) {
    val isStandard: Boolean get() = request?.startsWith("01") == true
    val stdPid: Int get() = request!!.substring(2).toInt(16)
    /** Формат строки: «Название,Кратко,Единицы,Максимум,Минимум,Масштаб» (как в Torque). */
    fun info(): String = String.format(Locale.US, "%s,%s,%s,%s,%s,1.0", name, shortName, unit, max, min)
}

object Live { @Volatile var baro = 101.0 }

private fun IntArray.u(i: Int): Int = if (i < size) this[i] else 0
private fun s16(h: Int, l: Int): Int { val v = h * 256 + l; return if (v > 32767) v - 65536 else v }

object MazdaPids {
    private val soot: Decoder = { (it.u(0) * 256 + it.u(1)) * 100 / 65536.0 }

    val all: List<PidDef> = listOf(
        // --- стандартные (режим 01, блок двигателя 7E0) ---
        PidDef("05", "Температура ОЖ", "ОЖ", "°C", -40.0, 130.0, request = "0105", decode = { it.u(0) - 40.0 }),
        PidDef("0c", "Обороты двигателя", "Обороты", "об/мин", 0.0, 5500.0, request = "010C", decode = { (it.u(0) * 256 + it.u(1)) / 4.0 }),
        PidDef("0d", "Скорость", "Скорость", "км/ч", 0.0, 240.0, request = "010D", decode = { it.u(0).toDouble() }),
        PidDef("04", "Нагрузка двигателя", "Нагрузка", "%", 0.0, 100.0, request = "0104", decode = { it.u(0) * 100 / 255.0 }),
        PidDef("23", "Давление в рампе", "Рампа", "МПа", 0.0, 200.0, request = "0123", decode = { (it.u(0) * 256 + it.u(1)) * 10 / 1000.0 }),
        PidDef("0b", "Давление во впуске абс", "Впуск", "кПа", 0.0, 255.0, request = "010B", decode = { it.u(0).toDouble() }),
        PidDef("33", "Атмосферное давление", "Атм", "кПа", 0.0, 120.0, request = "0133", slow = true,
            decode = { Live.baro = it.u(0).toDouble(); it.u(0).toDouble() }),
        PidDef("70", "Наддув избыточный", "Наддув", "бар", -1.0, 2.0, request = "0170",
            decode = { b -> if ((b.u(0) and 2) != 0) ((b.u(3) * 256 + b.u(4)) * 0.03125 - Live.baro) / 100.0 else null }),
        PidDef("10", "Расход воздуха", "MAF", "г/с", 0.0, 300.0, request = "0110", decode = { (it.u(0) * 256 + it.u(1)) / 100.0 }),
        PidDef("0f", "Температура воздуха на впуске", "IAT", "°C", -40.0, 100.0, request = "010F", decode = { it.u(0) - 40.0 }),
        PidDef("5c", "Температура масла двигателя", "Масло", "°C", -40.0, 150.0, request = "015C", decode = { it.u(0) - 40.0 }),
        PidDef("2f", "Уровень топлива", "Топливо", "%", 0.0, 100.0, request = "012F", slow = true, decode = { it.u(0) * 100 / 255.0 }),
        PidDef("42", "Напряжение бортсети", "АКБ", "В", 8.0, 16.0, request = "0142", slow = true, decode = { (it.u(0) * 256 + it.u(1)) / 1000.0 }),
        PidDef("7a", "Перепад давления на сажевом", "DPF dP", "кПа", 0.0, 50.0, request = "017A",
            decode = { b -> if ((b.u(0) and 1) != 0) s16(b.u(1), b.u(2)) / 100.0 else null }),

        // --- Mazda, режим 22 (подтверждено на автомобиле) ---
        PidDef("ff9001", "Счётчик регенераций DPF", "Регенер", "", 0.0, 10000.0, request = "220432", slow = true,
            decode = { (it.u(0) * 256 + it.u(1)).toDouble() }),
        PidDef("ff9002", "Пробег после прожига", "Пробег прож", "км", 0.0, 1500.0, request = "220434", slow = true,
            decode = { ((it.u(1) shl 16) + (it.u(2) shl 8) + it.u(3)) * 100 / 65536.0 }),
        PidDef("ff9003", "Накопление сажи", "Сажа", "г/л", 0.0, 10.0, request = "22042C", slow = true, decode = soot),
        PidDef("ff9004", "Генерация сажи", "Ген сажи", "г/л", 0.0, 10.0, request = "22042D", slow = true, decode = soot),
        // статус прожига сажевого: 0 нет, 1 обычный, 2 принудительный (по данным CarScanner для SkyActiv-D)
        PidDef("ff9006", "Статус прожига сажевого", "Прожиг", "", 0.0, 2.0, request = "220380", slow = true,
            decode = { it.u(0).toDouble() }),
        PidDef("ff9005", "Температура масла АКПП", "АКПП", "°C", -40.0, 150.0, header = "7E1", request = "221E1C", slow = true,
            decode = { s16(it.u(0), it.u(1)) / 16.0 }),

        // --- расчётные ---
        // Процент считается от порога dpf_full_gl (настройка, г/л). Пока порог не задан, значение пустое.
        PidDef("ff9010", "Заполнение сажевого в процентах", "DPF %", "%", 0.0, 100.0, slow = true,
            virtual = { e ->
                val lim = e.dpfFullGl()
                val acc = e.readId("ff9003")
                if (lim > 0 && acc != null) (acc / lim * 100).coerceIn(0.0, 150.0) else null
            }),
        PidDef("ff9011", "Интервал между прожигами последний", "Интервал", "км", 0.0, 1500.0, slow = true,
            virtual = { e -> e.tracker.lastInterval() }),
        PidDef("ff9012", "Интервал между прожигами средний", "Инт сред", "км", 0.0, 1500.0, slow = true,
            virtual = { e -> e.tracker.avgInterval(5) }),
        // экспериментальные: данные из журнала «сажа перед прожигом»
        PidDef("ff9013", "Сажа перед прожигом последняя (эксп.)", "Сажа до прож", "г/л", 0.0, 10.0, slow = true,
            virtual = { e -> e.tracker.lastSootPeak() }),
        PidDef("ff9014", "Сажа максимум с прошлого прожига (эксп.)", "Сажа макс", "г/л", 0.0, 10.0, slow = true,
            virtual = { e -> e.tracker.currentSootPeak() }),
    )

    private val index = all.associateBy { it.id.lowercase() }
    fun byId(id: String): PidDef? = index[id.lowercase()]
}
