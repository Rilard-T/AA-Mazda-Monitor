package com.aatorque.obd

import android.content.Context
import androidx.annotation.StringRes
import com.aatorque.stats.R
import java.util.Locale

typealias Decoder = (IntArray) -> Double?

/** Описание параметра. Либо запрос к блоку (request), либо расчётный (virtual). */
data class PidDef(
    val id: String,             // hex-подобный идентификатор для интерфейса в стиле Torque
    @StringRes val nameRes: Int,   // без запятых: они разделители в строке информации
    @StringRes val shortRes: Int,
    @StringRes val unitRes: Int,   // 0 — единиц нет
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
    /**
     * Формат строки: «Название,Кратко,Единицы,Максимум,Минимум,Масштаб» (как в Torque).
     * Названия берутся из ресурсов, поэтому подписи следуют языку телефона.
     */
    fun info(context: Context): String = String.format(
        Locale.US, "%s,%s,%s,%s,%s,1.0",
        context.getString(nameRes),
        context.getString(shortRes),
        if (unitRes != 0) context.getString(unitRes) else "",
        max, min
    )
}

object Live { @Volatile var baro = 101.0 }

private fun IntArray.u(i: Int): Int = if (i < size) this[i] else 0
private fun s16(h: Int, l: Int): Int { val v = h * 256 + l; return if (v > 32767) v - 65536 else v }

object MazdaPids {
    private val soot: Decoder = { (it.u(0) * 256 + it.u(1)) * 100 / 65536.0 }

    val all: List<PidDef> = listOf(
        // --- стандартные (режим 01, блок двигателя 7E0) ---
        PidDef("05", R.string.pid_05_name, R.string.pid_05_short, R.string.pid_05_unit, -40.0, 130.0,
            request = "0105", decode = { it.u(0) - 40.0 }),
        PidDef("0c", R.string.pid_0c_name, R.string.pid_0c_short, R.string.pid_0c_unit, 0.0, 5500.0,
            request = "010C", decode = { (it.u(0) * 256 + it.u(1)) / 4.0 }),
        PidDef("0d", R.string.pid_0d_name, R.string.pid_0d_short, R.string.pid_0d_unit, 0.0, 240.0,
            request = "010D", decode = { it.u(0).toDouble() }),
        PidDef("04", R.string.pid_04_name, R.string.pid_04_short, R.string.pid_04_unit, 0.0, 100.0,
            request = "0104", decode = { it.u(0) * 100 / 255.0 }),
        PidDef("23", R.string.pid_23_name, R.string.pid_23_short, R.string.pid_23_unit, 0.0, 200.0,
            request = "0123", decode = { (it.u(0) * 256 + it.u(1)) * 10 / 1000.0 }),
        PidDef("0b", R.string.pid_0b_name, R.string.pid_0b_short, R.string.pid_0b_unit, 0.0, 255.0,
            request = "010B", decode = { it.u(0).toDouble() }),
        PidDef("33", R.string.pid_33_name, R.string.pid_33_short, R.string.pid_33_unit, 0.0, 120.0,
            request = "0133", slow = true,
            decode = { Live.baro = it.u(0).toDouble(); it.u(0).toDouble() }),
        PidDef("70", R.string.pid_70_name, R.string.pid_70_short, R.string.pid_70_unit, -1.0, 2.0,
            request = "0170",
            decode = { b -> if ((b.u(0) and 2) != 0) ((b.u(3) * 256 + b.u(4)) * 0.03125 - Live.baro) / 100.0 else null }),
        PidDef("10", R.string.pid_10_name, R.string.pid_10_short, R.string.pid_10_unit, 0.0, 300.0,
            request = "0110", decode = { (it.u(0) * 256 + it.u(1)) / 100.0 }),
        PidDef("0f", R.string.pid_0f_name, R.string.pid_0f_short, R.string.pid_0f_unit, -40.0, 100.0,
            request = "010F", decode = { it.u(0) - 40.0 }),
        PidDef("5c", R.string.pid_5c_name, R.string.pid_5c_short, R.string.pid_5c_unit, -40.0, 150.0,
            request = "015C", decode = { it.u(0) - 40.0 }),
        PidDef("2f", R.string.pid_2f_name, R.string.pid_2f_short, R.string.pid_2f_unit, 0.0, 100.0,
            request = "012F", slow = true, decode = { it.u(0) * 100 / 255.0 }),
        PidDef("42", R.string.pid_42_name, R.string.pid_42_short, R.string.pid_42_unit, 8.0, 16.0,
            request = "0142", slow = true, decode = { (it.u(0) * 256 + it.u(1)) / 1000.0 }),
        PidDef("7a", R.string.pid_7a_name, R.string.pid_7a_short, R.string.pid_7a_unit, 0.0, 50.0,
            request = "017A",
            decode = { b -> if ((b.u(0) and 1) != 0) s16(b.u(1), b.u(2)) / 100.0 else null }),

        // --- Mazda, режим 22 (подтверждено на автомобиле) ---
        PidDef("ff9001", R.string.pid_ff9001_name, R.string.pid_ff9001_short, 0, 0.0, 10000.0,
            request = "220432", slow = true,
            decode = { (it.u(0) * 256 + it.u(1)).toDouble() }),
        PidDef("ff9002", R.string.pid_ff9002_name, R.string.pid_ff9002_short, R.string.pid_ff9002_unit, 0.0, 1500.0,
            request = "220434", slow = true,
            decode = { ((it.u(1) shl 16) + (it.u(2) shl 8) + it.u(3)) * 100 / 65536.0 }),
        PidDef("ff9003", R.string.pid_ff9003_name, R.string.pid_ff9003_short, R.string.pid_ff9003_unit, 0.0, 10.0,
            request = "22042C", slow = true, decode = soot),
        PidDef("ff9004", R.string.pid_ff9004_name, R.string.pid_ff9004_short, R.string.pid_ff9004_unit, 0.0, 10.0,
            request = "22042D", slow = true, decode = soot),
        // статус прожига сажевого: 0 нет, 1 обычный, 2 принудительный (по данным CarScanner для SkyActiv-D)
        PidDef("ff9006", R.string.pid_ff9006_name, R.string.pid_ff9006_short, 0, 0.0, 2.0,
            request = "220380", slow = true,
            decode = { it.u(0).toDouble() }),
        PidDef("ff9005", R.string.pid_ff9005_name, R.string.pid_ff9005_short, R.string.pid_ff9005_unit, -40.0, 150.0,
            header = "7E1", request = "221E1C", slow = true,
            decode = { s16(it.u(0), it.u(1)) / 16.0 }),

        // --- расчётные ---
        // Процент считается от порога dpf_full_gl (настройка, г/л). Пока порог не задан, значение пустое.
        PidDef("ff9010", R.string.pid_ff9010_name, R.string.pid_ff9010_short, R.string.pid_ff9010_unit, 0.0, 100.0,
            slow = true,
            virtual = { e ->
                val lim = e.dpfFullGl()
                val acc = e.readId("ff9003")
                if (lim > 0 && acc != null) (acc / lim * 100).coerceIn(0.0, 150.0) else null
            }),
        PidDef("ff9011", R.string.pid_ff9011_name, R.string.pid_ff9011_short, R.string.pid_ff9011_unit, 0.0, 1500.0,
            slow = true,
            virtual = { e -> e.tracker.lastInterval() }),
        PidDef("ff9012", R.string.pid_ff9012_name, R.string.pid_ff9012_short, R.string.pid_ff9012_unit, 0.0, 1500.0,
            slow = true,
            virtual = { e -> e.tracker.avgInterval(5) }),
        // экспериментальные: данные из журнала «сажа перед прожигом»
        PidDef("ff9013", R.string.pid_ff9013_name, R.string.pid_ff9013_short, R.string.pid_ff9013_unit, 0.0, 10.0,
            slow = true,
            virtual = { e -> e.tracker.lastSootPeak() }),
        PidDef("ff9014", R.string.pid_ff9014_name, R.string.pid_ff9014_short, R.string.pid_ff9014_unit, 0.0, 10.0,
            slow = true,
            virtual = { e -> e.tracker.currentSootPeak() }),
    )

    private val index = all.associateBy { it.id.lowercase() }
    fun byId(id: String): PidDef? = index[id.lowercase()]
}
