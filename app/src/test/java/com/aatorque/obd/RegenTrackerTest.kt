package com.aatorque.obd

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверки журнала прожигов на подставных настройках: без Android и без таймеров. */
class RegenTrackerTest {

    /** Минимальная замена SharedPreferences: хватает для save() и чтения начального состояния. */
    private class FakePrefs(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
        private val map = HashMap<String, Any?>(initial)

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        inner class Editor : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private val removals = HashSet<String>()

            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { removals.add(key) }
            override fun clear() = apply { map.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                removals.forEach { map.remove(it) }
                map.putAll(pending)
                pending.clear()
                removals.clear()
            }
        }
    }

    @Test
    fun counterGrowthRecordsInterval() {
        // отсчёт расстояния с прошлого прожига: ровно этот пробег должен попасть в интервалы
        val tracker = RegenTracker(FakePrefs(mapOf("rg_count" to 3)))
        tracker.onDist(40.0)
        tracker.onDist(120.0)
        tracker.onCount(4)
        assertEquals(120.0, tracker.lastInterval()!!, 0.001)
    }

    @Test
    fun smallDistanceIsNotRecordedAsInterval() {
        val tracker = RegenTracker(FakePrefs(mapOf("rg_count" to 1)))
        tracker.onDist(0.4)
        tracker.onCount(2)
        assertNull(tracker.lastInterval())
    }

    @Test
    fun counterGrowthWithoutStatusWritesLogEntry() {
        // статус прожига от блока не приходит: запись делается по росту счётчика
        val tracker = RegenTracker(FakePrefs(mapOf("rg_count" to 7)))
        repeat(3) { tracker.onSoot(2.0) }
        tracker.onCount(8)
        val entry = tracker.history().last()
        assertEquals(RegenState.BY_COUNTER, entry.state)
        assertEquals(2.0, entry.soot, 0.001)
    }

    @Test
    fun zeroSootIsNotLogged() {
        val tracker = RegenTracker(FakePrefs(mapOf("rg_count" to 9)))
        repeat(3) { tracker.onSoot(0.0) }
        tracker.onCount(10)
        assertEquals(0, tracker.history().size)
    }

    @Test
    fun counterGrowthCompletesRunningEntry() {
        val tracker = RegenTracker(FakePrefs(mapOf("rg_count" to 20)))
        assertEquals(1, tracker.importText("L;1000;5.5;123.4;0"))
        assertEquals(RegenState.RUNNING, tracker.history().last().state)
        tracker.onCount(21)
        assertEquals(RegenState.COMPLETE, tracker.history().last().state)
    }

    @Test
    fun importKeepsLastEntriesAndSurvivesRoundTrip() {
        val tracker = RegenTracker(FakePrefs())
        tracker.importText("L;1000;5.5;123.4;1\nL;2000;3.0;;3")
        val exported = tracker.exportText()
        assertEquals(2, RegenTracker(FakePrefs()).importText(exported))
    }

    @Test
    fun statusMustHoldThirtySecondsBeforeFlag() {
        // статус 1/2 короче 30 секунд — это ещё не прожиг: флаг не ставим и запись не создаём
        var now = 1_000_000L
        val tracker = RegenTracker(FakePrefs(mapOf("rg_count" to 1))) { now }
        repeat(3) { tracker.onSoot(4.0) }
        tracker.onStatus(0)   // обычная связь без прожига: сессия уже идёт
        tracker.onStatus(2)
        now += 29_000
        tracker.onStatus(2)
        assertFalse(tracker.regenActive())
        assertEquals(0, tracker.history().size)
        now += 1_000
        tracker.onStatus(1)   // выдержка набирается любым статусом 1/2
        assertTrue(tracker.regenActive())
        assertEquals(RegenState.RUNNING, tracker.history().last().state)
    }

    @Test
    fun connectingMidRegenerationWaitsBeforeFlag() {
        // подключились, когда прожиг уже шёл: флаг тоже только после выдержки, запись не ведём
        var now = 2_000_000L
        val tracker = RegenTracker(FakePrefs(mapOf("rg_count" to 1))) { now }
        repeat(3) { tracker.onSoot(6.0) }
        tracker.onStatus(2)
        assertFalse(tracker.regenActive())
        now += 31_000
        tracker.onStatus(2)
        assertTrue(tracker.regenActive())
        assertEquals(0, tracker.history().size)
    }

    @Test
    fun invalidSootValuesAreIgnored() {
        val tracker = RegenTracker(FakePrefs(mapOf("rg_count" to 2)))
        repeat(3) { tracker.onSoot(35.0) }   // выше рабочего диапазона — мусор
        tracker.onCount(3)
        assertEquals(0, tracker.history().size)
    }
}
