package com.aatorque.stats

import android.util.Log
import timber.log.Timber

class CacheLogTree: Timber.DebugTree() {

    companion object {
        const val KEEP_LOGS = 5_000
        // строки-шум, которые вытесняли из буфера нужные записи (6-7 раз в секунду)
        private val NOISE = listOf("Got valid")
    }

    data class LogDesc(
        val priority: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable?,
        val time: Long = System.currentTimeMillis(),
    )

    private val lock = Any()
    val logCache = ArrayDeque<LogDesc>(KEEP_LOGS)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (BuildConfig.DEBUG) {
            super.log(priority, "AATORQUE:${tag ?: ""}", message, t)
        }
        if (t == null && NOISE.any { message.contains(it) }) return
        synchronized(lock) {
            logCache.add(LogDesc(priority, tag, message, t))
            while (logCache.size > KEEP_LOGS) {
                logCache.removeFirst()
            }
        }
    }

    fun snapshot(): List<LogDesc> = synchronized(lock) { logCache.toList() }

    fun logToString(): Array<String> {
        return snapshot().map {
            return@map try {
                "${it.tag ?: ""}: ${levelName(it.priority)} ${it.message} ${it.throwable?.stackTraceToString() ?: ""}"
            } catch (e: NullPointerException) {
                ""
            }
        }.toTypedArray()
    }

    fun levelName(priority: Int): String = when (priority) {
        Log.ERROR -> "Error"
        Log.DEBUG -> "Debug"
        Log.WARN -> "Warn"
        Log.INFO -> "Info"
        Log.VERBOSE -> "Verbose"
        Log.ASSERT -> "Assert"
        else -> "Unknown"
    }
}
