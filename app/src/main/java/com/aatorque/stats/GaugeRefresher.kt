package com.aatorque.stats
import android.os.Handler
import android.os.Looper
import com.aatorque.datastore.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

enum class ConnectStatus {
    CONNECTING_SERVICE, CONNECTING_ECU, CONNECTED, SETUP_GAUGE
}

/**
 * Обновление приборов. Один таймер на экран и один пакетный запрос всех датчиков:
 * ELM327 не успевает за семью отдельными опросами по 300 мс, из-за чего росла очередь
 * и значения отставали от реальных.
 */
class GaugeRefresher {
    /** Ключ — позиция элемента на экране; меняется из главного потока, читается из таймера. */
    val data = ConcurrentHashMap<Int, GaugeData>()
    private val executor = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    val handler = Handler(Looper.getMainLooper())
    private val conWatcher = MutableStateFlow(ConnectStatus.CONNECTING_SERVICE)
    val connectStatus = conWatcher.asSharedFlow()
    val cache = HashMap<Int, HashMap<Int, GaugeData>>()
    private var batchTimer: ScheduledFuture<*>? = null

    companion object {
        const val REFRESH_INTERVAL = 300L
    }

    fun populateQuery(pos: Int, screen: Int, query: Display): GaugeData {
        data[pos]?.stopRefreshing(true)
        val cacheItem = cache[screen]?.get(pos)
        val torqueData = if (cacheItem?.display?.equals(query) == true) {
            cacheItem
        } else {
            GaugeData(query)
        }
        cache.getOrPut(screen) {
            HashMap()
        }[pos] = torqueData
        data[pos] = torqueData
        Timber.i("Setting query: $query for pos $pos on screen $screen")
        return torqueData
    }

    suspend fun makeExecutors(service: ObdServiceClient) {
        val foundValid = data.values.any { it.pid != null }
        if (foundValid && batchTimer == null) {
            Timber.i("Scheduled batch refresh for ${data.size} items")
            batchTimer = executor.scheduleWithFixedDelay({
                try {
                    doRefreshBatch(service)
                } catch (e: Exception) {
                    Timber.e("Refresh failed", e)
                }
            }, 0, REFRESH_INTERVAL, TimeUnit.MILLISECONDS)
        }
        if (!foundValid) conWatcher.value = ConnectStatus.SETUP_GAUGE
        service.addConnectCallback {
            if (conWatcher.value == ConnectStatus.CONNECTING_SERVICE) {
                conWatcher.value = ConnectStatus.CONNECTING_ECU
            }
        }
    }

    /** Один запрос на все датчики экрана; ответ раскладывается по элементам в том же порядке. */
    private fun doRefreshBatch(service: ObdServiceClient) {
        val items = data.entries
            .mapNotNull { (pos, torqueData) -> torqueData.pid?.let { pos to it } }
            .sortedBy { it.first }
        if (items.isEmpty()) return
        service.runIfConnected { ts ->
            val values = try {
                ts.getPIDValuesAsDouble(items.map { it.second }.toTypedArray())
            } catch (e: Exception) {
                Timber.e("OBD service returned invalid data for ${items.map { it.second }}", e)
                return@runIfConnected
            }
            var anyValue = false
            items.forEachIndexed { index, (pos, pid) ->
                val torqueData = data[pos] ?: return@forEachIndexed
                val value = values.getOrNull(index) ?: return@forEachIndexed
                torqueData.lastData = value
                Timber.d("Got valid $value from OBD for ${torqueData.display.label}")
                if (value != 0.0 || torqueData.hasReceivedNonZero) {
                    torqueData.hasReceivedNonZero = true
                    anyValue = true
                    handler.post {
                        torqueData.sendNotifyUpdate()
                    }
                }
            }
            if (anyValue) conWatcher.value = ConnectStatus.CONNECTED
        }
    }

    fun stopExecutors() {
        Timber.i("Telling gauge refreshers to stop")
        batchTimer?.cancel(true)
        batchTimer = null
        for (td in data.values) {
            td.stopRefreshing()
        }
    }

    /** Экран уничтожен: таймер и поток исполнителя больше не нужны. */
    fun shutdown() {
        stopExecutors()
        executor.shutdownNow()
    }

    fun hasChanged(idx: Int, otherScreen: Display?): Boolean {
        if (!data.containsKey(idx)) return true
        return data[idx]?.display?.equals(otherScreen) != true
    }

    fun updateIfNeeded(pos: Int, screen: Int, query: Display): GaugeData {
        return if (hasChanged(pos, query)) {
            populateQuery(pos, screen, query)
        } else {
            data[pos]!!
        }
    }

}
