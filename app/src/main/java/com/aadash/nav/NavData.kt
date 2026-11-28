package com.aadash.nav

import android.os.SystemClock
import com.aadash.obd.MazdaPids
import com.aadash.obd.ObdBridgeService
import com.aadash.stats.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

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

    /** Срез истории начиная с [cutoff] без лишней промежуточной фильтрации. */
    fun historySince(id: String, cutoff: Long): List<Pair<Long, Double>> {
        val buf = history[id] ?: return emptyList()
        synchronized(buf) {
            if (buf.isEmpty()) return emptyList()
            val out = ArrayList<Pair<Long, Double>>(buf.size)
            for (p in buf) {
                if (p.first >= cutoff) out.add(p)
            }
            return out
        }
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

    private fun simulatedValue(id: String, now: Long): Double? {
        val pd = MazdaPids.byId(id) ?: return null
        val t = now / 1000.0
        val phase = (id.hashCode() and 0xFF) * 0.17
        val wave = 0.5 + 0.45 * sin(t * 0.45 + phase)
        return when (id.lowercase()) {
            "0c" -> 800.0 + 3600.0 * (0.5 + 0.45 * sin(t * 0.7))
            "0d" -> 20.0 + 110.0 * (0.5 + 0.45 * sin(t * 0.35 + 1.0))
            "05" -> 76.0 + 16.0 * (0.5 + 0.45 * sin(t * 0.12))
            "ff9005" -> 55.0 + 25.0 * (0.5 + 0.45 * sin(t * 0.10 + 0.7))
            "ff9003" -> 3.2 + 2.5 * (0.5 + 0.45 * sin(t * 0.08))
            "ff9010" -> 50.0 + 40.0 * (0.5 + 0.45 * sin(t * 0.08))
            "ff9002" -> 740.0 + (t * 0.02)
            else -> pd.min + (pd.max - pd.min) * wave
        }
    }

    private fun loop() {
        var lastSlow = 0L
        var lastHistory = 0L
        while (running) {
            try {
                val now = SystemClock.uptimeMillis()
                for (id in NavLayoutStore.fastPids) {
                    val v = ObdBridgeService.peek(id) ?: if (BuildConfig.SIMULATE_METRICS) simulatedValue(id, now) else null
                    put(id, v)
                }
                if (now - lastSlow > 1000L) {
                    for (id in NavLayoutStore.slowPids) {
                        val v = ObdBridgeService.peek(id) ?: if (BuildConfig.SIMULATE_METRICS) simulatedValue(id, now) else null
                        put(id, v)
                    }
                    if (BuildConfig.SIMULATE_METRICS && texts["media_title"].isNullOrBlank()) {
                        texts["media_title"] = "Demo Artist — SkyActiv-D"
                    }
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
