package com.aatorque.obd

import android.content.SharedPreferences

/**
 * Torque-стиль: после подключения опрашиваем нестандартные датчики и запоминаем,
 * на какие блок не ответил. Результат хранится между подключениями, чтобы при
 * следующем старте сразу знать профиль датчиков конкретной машины.
 */
object PidProbe {

    private const val KEY_MISSING = "probe_missing"
    private const val KEY_DONE = "probe_done"

    @Volatile private var missing: Set<String> = emptySet()

    /** Опрос завершён: список «нет ответа» известен. */
    @Volatile var done: Boolean = false
        private set

    fun isMissing(id: String): Boolean = missing.contains(id.lowercase())

    fun missingIds(): Set<String> = missing

    /** Загружает результат прошлого опроса: датчики сразу помечены, пока идёт новый опрос. */
    fun load(prefs: SharedPreferences) {
        missing = prefs.getString(KEY_MISSING, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.map { it.lowercase() }
            ?.toSet()
            ?: emptySet()
        done = prefs.getBoolean(KEY_DONE, false)
    }

    /** Сохраняет результат опроса для следующего подключения. */
    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString(KEY_MISSING, missing.sorted().joinToString(","))
            .putBoolean(KEY_DONE, done)
            .apply()
    }

    internal fun onAnswer(id: String, answered: Boolean) {
        val key = id.lowercase()
        missing = if (answered) missing - key else missing + key
    }

    internal fun reset() {
        missing = emptySet()
        done = false
    }

    /** Новый опрос начался: старый результат остаётся видимым до завершения. */
    internal fun begin() {
        done = false
    }

    internal fun finish() {
        done = true
    }
}
