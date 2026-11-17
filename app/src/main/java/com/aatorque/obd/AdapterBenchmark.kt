package com.aatorque.obd

/**
 * Замер адаптера: что за ELM327, на каком протоколе он отвечает и сколько миллисекунд
 * занимает один запрос. Нужен, чтобы понять, почему стрелки дёргаются, и подобрать
 * частоту опроса под конкретный адаптер.
 */
data class AdapterBenchmark(
    val adapter: String,
    val protocol: String,
    val minMs: Long,
    val avgMs: Long,
    val maxMs: Long,
    /** Сколько запросов из серии дали ответ. Ноль — блок молчит (например, выключено зажигание). */
    val samples: Int,
    val adaptiveTiming: Boolean,
    /** Сколько PID режима 01 заявил блок двигателя; -1 — ещё не известно. */
    val supported: Int,
) {
    /** Предельная частота опроса одного датчика, раз в секунду. */
    val hz: Double get() = if (avgMs > 0) 1000.0 / avgMs else 0.0

    enum class Quality { EXCELLENT, GOOD, MODERATE, SLOW }

    val quality: Quality get() = classify(avgMs)

    companion object {
        /**
         * Оценка по средней задержке: до 80 мс — стрелки успевают за оборотами, после 300 мс
         * на экран стоит вынести 3–5 самых нужных датчиков.
         */
        fun classify(avgMs: Long): Quality = when {
            avgMs <= 0 -> Quality.SLOW
            avgMs <= 80 -> Quality.EXCELLENT
            avgMs <= 160 -> Quality.GOOD
            avgMs <= 300 -> Quality.MODERATE
            else -> Quality.SLOW
        }
    }
}
