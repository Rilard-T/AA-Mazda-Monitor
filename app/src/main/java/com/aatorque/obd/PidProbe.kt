package com.aatorque.obd

/**
 * Torque-стиль: после подключения опрашиваем нестандартные датчики и запоминаем,
 * на какие блок не ответил. Пока опрос не завершён, все датчики считаются доступными.
 */
object PidProbe {

    @Volatile private var missing: Set<String> = emptySet()

    /** Опрос завершён: список «нет ответа» известен. */
    @Volatile var done: Boolean = false
        private set

    fun isMissing(id: String): Boolean = missing.contains(id.lowercase())

    fun missingIds(): Set<String> = missing

    internal fun onAnswer(id: String, answered: Boolean) {
        val key = id.lowercase()
        missing = if (answered) missing - key else missing + key
    }

    internal fun reset() {
        missing = emptySet()
        done = false
    }

    internal fun finish() {
        done = true
    }
}
