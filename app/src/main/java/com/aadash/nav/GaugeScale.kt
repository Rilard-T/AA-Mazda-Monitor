package com.aadash.nav

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Расчёт шага подписей шкалы прибора. Чистые функции без Android — проверяются на JVM.
 */
object GaugeScale {

    /**
     * Круглый шаг подписей: 1, 2, 2.5, 5 и их степени десятки.
     * Берём шаг, который делит диапазон и минимум нацело и даёт 4–10 делений;
     * если такого нет (например 240 км/ч) — делим диапазон на восемь частей.
     *
     * @return шаг подписей и их количество (включая обе крайние)
     */
    fun ticks(range: Float, min: Float): Pair<Float, Int> {
        if (range <= 0f) return 1f to 2
        val steps = ArrayList<Float>(32)
        var base = 0.1f
        repeat(6) {
            for (m in floatArrayOf(1f, 2f, 2.5f, 5f)) steps.add(base * m)
            base *= 10f
        }
        for (s in steps) {
            val count = range / s
            val ticks = count.roundToInt()
            if (ticks !in 3..9 || abs(count - ticks) > 1e-3f) continue
            val minTicks = min / s
            if (abs(minTicks - minTicks.roundToInt()) > 1e-3f) continue
            return s to ticks + 1
        }
        return range / 8f to 9
    }
}
