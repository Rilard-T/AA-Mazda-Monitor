package com.aatorque.nav

import android.os.SystemClock
import com.aatorque.obd.ObdBridgeService
import java.util.concurrent.ConcurrentHashMap

/** Фоновый опрос значений из ObdBridgeService. Рисование читает только готовые значения и не ждёт адаптер. */
object NavData {
    private val FAST = arrayOf("0d", "0c", "05")
    private val SLOW = arrayOf("ff9010", "ff9003", "ff9005", "ff9011")

    private val values = ConcurrentHashMap<String, Double>()
    @Volatile private var running = false
    private var thread: Thread? = null

    fun get(id: String): Double? = values[id]

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

    private fun loop() {
        var lastSlow = 0L
        while (running) {
            try {
                for (id in FAST) put(id, ObdBridgeService.peek(id))
                val now = SystemClock.uptimeMillis()
                if (now - lastSlow > 1000L) {
                    for (id in SLOW) put(id, ObdBridgeService.peek(id))
                    lastSlow = now
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
