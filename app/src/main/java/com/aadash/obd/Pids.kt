package com.aadash.obd

import android.content.Context
import androidx.annotation.StringRes
import com.aadash.stats.R
import java.util.Locale

typealias Decoder = (IntArray) -> Double?

/** Описание параметра. Либо запрос к блоку (request), либо расчётный (virtual). */
data class PidDef(
    val id: String,             // hex-подобный идентификатор для интерфейса в стиле Torque
    @StringRes val nameRes: Int = 0,   // без запятых: они разделители в строке информации
    @StringRes val shortRes: Int = 0,
    @StringRes val unitRes: Int = 0,   // 0 — единиц нет
    val min: Double,
    val max: Double,
    val header: String = "7E0", // 7E0 — двигатель, 7E1 — АКПП
    val request: String? = null,// "010C" (режим 01) или "220434" (режим 22)
    val slow: Boolean = false,  // медленные параметры кэшируются дольше
    val decode: Decoder? = null,
    val virtual: ((ObdEngine) -> Double?)? = null,
    // Подписи пользовательских PID: у них нет строковых ресурсов, потому что название вводит сам
    // пользователь. Для встроенных датчиков остаются nameRes/shortRes/unitRes.
    val label: String = "",
    val shortLabel: String = "",
    val unitLabel: String = "",
) {
    val isStandard: Boolean get() = request?.startsWith("01") == true
    val stdPid: Int get() = request?.takeIf { it.startsWith("01") }?.substring(2)?.toIntOrNull(16) ?: -1

    /** Название датчика на языке телефона (или введённое пользователем). */
    fun name(context: Context): String =
        if (label.isNotEmpty()) label else context.getString(nameRes)

    /** Короткое имя для приборов и шкал; у пользовательских PID его может не быть. */
    fun short(context: Context): String = when {
        shortLabel.isNotEmpty() -> shortLabel
        shortRes != 0 -> context.getString(shortRes)
        else -> name(context)
    }

    /** Единицы измерения; пустая строка — единиц нет. */
    fun unit(context: Context): String = when {
        unitLabel.isNotEmpty() -> unitLabel
        unitRes != 0 -> context.getString(unitRes)
        else -> ""
    }

    /**
     * Формат строки: «Название,Кратко,Единицы,Максимум,Минимум,Масштаб» (как в Torque).
     * Названия берутся из ресурсов, поэтому подписи следуют языку телефона.
     */
    fun info(context: Context): String = String.format(
        Locale.US, "%s,%s,%s,%s,%s,1.0",
        name(context),
        short(context),
        unit(context),
        max, min
    )
}

object Live { @Volatile var baro = 101.0 }

private fun IntArray.u(i: Int): Int = if (i < size) this[i] else 0
object MazdaPids {
    private val soot: Decoder = { MazdaDecode.soot(it) }

    /** Встроенный каталог Mazda; пользовательские PID добавляются в [all] поверх него. */
    private val builtin: List<PidDef> = listOf(
        // --- стандартные (режим 01, блок двигателя 7E0) ---
        PidDef("05", R.string.pid_05_name, R.string.pid_05_short, R.string.pid_05_unit, -40.0, 130.0,
            request = "0105", decode = { MazdaDecode.at(it.u(0)) }),
        PidDef("0c", R.string.pid_0c_name, R.string.pid_0c_short, R.string.pid_0c_unit, 0.0, 5500.0,
            request = "010C", decode = { MazdaDecode.rpm(it) }),
        PidDef("0d", R.string.pid_0d_name, R.string.pid_0d_short, R.string.pid_0d_unit, 0.0, 240.0,
            request = "010D", decode = { it.u(0).toDouble() }),
        PidDef("04", R.string.pid_04_name, R.string.pid_04_short, R.string.pid_04_unit, 0.0, 100.0,
            request = "0104", decode = { MazdaDecode.percent(it.u(0)) }),
        PidDef("23", R.string.pid_23_name, R.string.pid_23_short, R.string.pid_23_unit, 0.0, 200.0,
            request = "0123", decode = { MazdaDecode.railPressure(it) }),
        PidDef("0b", R.string.pid_0b_name, R.string.pid_0b_short, R.string.pid_0b_unit, 0.0, 255.0,
            request = "010B", decode = { it.u(0).toDouble() }),
        PidDef("33", R.string.pid_33_name, R.string.pid_33_short, R.string.pid_33_unit, 0.0, 120.0,
            request = "0133", slow = true,
            decode = { Live.baro = it.u(0).toDouble(); it.u(0).toDouble() }),
        PidDef("70", R.string.pid_70_name, R.string.pid_70_short, R.string.pid_70_unit, -1.0, 2.0,
            request = "0170",
            decode = { b -> MazdaDecode.boost(b, Live.baro) }),
        PidDef("10", R.string.pid_10_name, R.string.pid_10_short, R.string.pid_10_unit, 0.0, 300.0,
            request = "0110", decode = { MazdaDecode.maf(it) }),
        PidDef("0f", R.string.pid_0f_name, R.string.pid_0f_short, R.string.pid_0f_unit, -40.0, 100.0,
            request = "010F", decode = { MazdaDecode.at(it.u(0)) }),
        PidDef("5c", R.string.pid_5c_name, R.string.pid_5c_short, R.string.pid_5c_unit, -40.0, 150.0,
            request = "015C", slow = true, decode = { MazdaDecode.at(it.u(0)) }),
        PidDef("2f", R.string.pid_2f_name, R.string.pid_2f_short, R.string.pid_2f_unit, 0.0, 100.0,
            request = "012F", slow = true, decode = { MazdaDecode.percent(it.u(0)) }),
        PidDef("42", R.string.pid_42_name, R.string.pid_42_short, R.string.pid_42_unit, 8.0, 16.0,
            request = "0142", slow = true, decode = { MazdaDecode.voltage(it) }),
        PidDef("7a", R.string.pid_7a_name, R.string.pid_7a_short, R.string.pid_7a_unit, 0.0, 50.0,
            request = "017A",
            decode = { b -> MazdaDecode.dpfPressure(b) }),

        // --- Mazda, режим 22 (подтверждено на автомобиле) ---
        PidDef("ff9001", R.string.pid_ff9001_name, R.string.pid_ff9001_short, 0, 0.0, 10000.0,
            request = "220432", slow = true,
            decode = { MazdaDecode.regenCounter(it) }),
        PidDef("ff9002", R.string.pid_ff9002_name, R.string.pid_ff9002_short, R.string.pid_ff9002_unit, 0.0, 1500.0,
            request = "220434", slow = true,
            decode = { MazdaDecode.kmSinceRegen(it) }),
        PidDef("ff9003", R.string.pid_ff9003_name, R.string.pid_ff9003_short, R.string.pid_ff9003_unit, 0.0, 10.0,
            request = "22042C", slow = true, decode = soot),
        PidDef("ff9004", R.string.pid_ff9004_name, R.string.pid_ff9004_short, R.string.pid_ff9004_unit, 0.0, 10.0,
            request = "22042D", slow = true, decode = soot),
        // статус прожига сажевого (DID 0380): 0 нет прожига; 1 — активная регенерация (пост-инжекция);
        // 2 — REG_REQ_A (запрос автоматической регенерации по FORScan: движение >15 км/ч, EOT>80 °C,
        // PM_ACC_DSD/PM_GEN > ~5.6 г/л). На некоторых прошивках значение 2 сопровождает активную
        // регенерацию, поэтому в RegenTracker оба трактуются как «ненулевой», но различаются в журнале.
        PidDef("ff9006", R.string.pid_ff9006_name, R.string.pid_ff9006_short, 0, 0.0, 2.0,
            request = "220380", slow = true,
            decode = { MazdaDecode.regenStatus(it) }),
        PidDef("ff9005", R.string.pid_ff9005_name, R.string.pid_ff9005_short, R.string.pid_ff9005_unit, -40.0, 150.0,
            header = "7E1", request = "221E1C", slow = true,
            decode = { MazdaDecode.transTemp(it) }),

        // --- SkyActiv-D: набор Torque Pro (проверено на Mazda2 1.5 S5-DPTS) ---
        PidDef("ff9015", R.string.pid_ff9015_name, R.string.pid_ff9015_short, R.string.pid_ff9015_unit, 0.0, 10.0,
            request = "22042B", slow = true, decode = soot),
        PidDef("ff9016", R.string.pid_ff9016_name, R.string.pid_ff9016_short, R.string.pid_ff9016_unit, -20.0, 50.0,
            request = "22F47A", slow = true, decode = { MazdaDecode.dpfDiffPressure(it) }),
        PidDef("ff9017", R.string.pid_ff9017_name, R.string.pid_ff9017_short, R.string.pid_ff9017_unit, 0.0, 100.0,
            request = "22F469", slow = true, decode = { MazdaDecode.egrA(it) }),
        PidDef("ff9018", R.string.pid_ff9018_name, R.string.pid_ff9018_short, R.string.pid_ff9018_unit, 0.0, 100.0,
            request = "22F469", slow = true, decode = { MazdaDecode.egrB(it) }),
        PidDef("ff9019", R.string.pid_ff9019_name, R.string.pid_ff9019_short, R.string.pid_ff9019_unit, 0.0, 100.0,
            request = "22F411", slow = true, decode = { MazdaDecode.intakeShutter(it) }),
        PidDef("ff901a", R.string.pid_ff901a_name, R.string.pid_ff901a_short, R.string.pid_ff901a_unit, 0.0, 1000000.0,
            request = "22DD01", slow = true, decode = { MazdaDecode.totalDistance(it) }),
        PidDef("ff901b", R.string.pid_ff901b_name, R.string.pid_ff901b_short, R.string.pid_ff901b_unit, 0.0, 100.0,
            request = "224028", slow = true, decode = { MazdaDecode.batterySoc(it) }),

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

    private var version = -1
    private var combined: List<PidDef> = builtin
    private var index: Map<String, PidDef> = builtin.associateBy { it.id.lowercase() }

    /** Пересобирает каталог, если список пользовательских PID изменился. */
    private fun refresh() {
        val rev = UserPids.revision
        if (rev == version) return
        val extra = UserPids.defs()
        combined = builtin + extra
        index = combined.associateBy { it.id.lowercase() }
        version = rev
    }

    /** Все датчики: встроенные Mazda и добавленные пользователем. */
    val all: List<PidDef> get() = synchronized(this) { refresh(); combined }

    fun byId(id: String): PidDef? = synchronized(this) {
        refresh()
        index[id.lowercase()]
    }
}
