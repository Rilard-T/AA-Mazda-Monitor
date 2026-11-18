package com.aatorque.nav

import android.os.SystemClock
import com.aatorque.obd.ObdBridgeService
import java.util.concurrent.ConcurrentHashMap

/** Фоновый опрос значений из ObdBridgeService. Рисование читает только готовые значения и не ждёт адаптер.
 *  Какие параметры опрашивать, берётся из раскладки: приборы часто (каждые ~30 мс), значения раз в секунду.
 *  История значений хранится в кольцевом буфере по каждому pid (для графиков в Android Auto).
 */
object NavData {
    private val values = ConcurrentHashMap<String, Double>()
    private val texts = ConcurrentHashMap<String, String>()
    private val history = ConcurrentHashMap<String, ArrayDeque<Pair<Long, Double>>>()
    private const val HISTORY_LIMIT = 600
    @Volatile private var running = false
    private var thread: Thread? = null

    fun get(id: String): Double? = values[id]

    fun getText(id: String): String? = texts[id]

    fun setText(id: String, text: String?) {
        if (text.isNullOrBlank()) texts.remove(id) else texts[id] = text
    }

    /** Снимок истории значений pid за последние ~10 мин (время в uptimeMillis, значение). */
    fun history(id: String): List<Pair<Long, Double>> {
        val buf = history[id] ?: return emptyList()
        synchronized(buf) { return ArrayList(buf) }
    }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        val t = Thread({ loop() }, "nav-data")
        t.isDaemon = true
        thread = t
        t.start()
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun put(id: String, v: Double?) {
        if (v == null || v.isNaN()) values.remove(id) else values[id] = v
    }

    private fun recordHistory(now: Long) {
        val ids = (NavLayoutStore.fastPids + NavLayoutStore.slowPids).toSet()
        for (id in ids) {
            val v = values[id] ?: continue
            val buf = history.getOrPut(id) { ArrayDeque() }
            synchronized(buf) {
                if (buf.isEmpty() || buf.last().second != v) {
                    buf.addLast(now to v)
                    while (buf.size > HISTORY_LIMIT) buf.removeFirst()
                }
            }
        }
    }

    private fun loop() {
        var lastSlow = 0L
        var lastHistory = 0L
        while (running) {
            try {
                for (id in NavLayoutStore.fastPids) put(id, ObdBridgeService.peek(id))
                val now = SystemClock.uptimeMillis()
                if (now - lastSlow > 1000L) {
                    for (id in NavLayoutStore.slowPids) put(id, ObdBridgeService.peek(id))
                    lastSlow = now
                }
                if (now - lastHistory > 1000L) {
                    recordHistory(now)
                    lastHistory = now
                }
                Thread.sleep(30)
            } catch (e: InterruptedException) {
                return
            } catch (e: Exception) {
                try {
                    Thread.sleep(500)
                } catch (ie: InterruptedException) {
                    return
                }
            }
        }
    }
}
