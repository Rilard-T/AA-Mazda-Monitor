package com.aatorque.obd

import android.content.SharedPreferences
import timber.log.Timber

/** Состояния записи о прожиге. */
object RegenState {
    const val RUNNING = 0      // прожиг начался (по статусу блока), ещё не завершён
    const val COMPLETE = 1     // после него вырос счётчик регенераций
    const val INTERRUPTED = 2  // прожиг прерван (двигатель выключили во время прожига или счётчик не вырос)
    const val BY_COUNTER = 3   // статус прожига недоступен, запись сделана по росту счётчика
}

/** Запись о прожиге: когда включился, сажа перед ним (г/л), пробег после прошлого прожига (км), состояние, принудительный ли. */
data class RegenEntry(val time: Long, val soot: Double, val km: Double?, val state: Int, val forced: Boolean = false)

/**
 * Журнал прожигов. Одна копия на процесс (служба и экраны читают одно и то же).
 * Интервал между прожигами: максимальный «пробег после прожига» к моменту роста счётчика.
 * Экспериментально: запись сажи делается в момент ВКЛЮЧЕНИЯ прожига (статус блока не 0),
 * по максимуму сажи, накопленному до этого момента.
 */
class RegenTracker private constructor(private val prefs: SharedPreferences) {
    private var lastCount: Int? = prefs.getInt("rg_count", -1).takeIf { it >= 0 }
    private var peak = prefs.getFloat("rg_peak", 0f).toDouble()
    private var lastSeen = prefs.getFloat("rg_seen", 0f).toDouble()
    private var awaitReset = false
    private val intervals = ArrayList<Double>().apply {
        prefs.getString("rg_hist", "")!!.split(",").filter { it.isNotBlank() }.mapTo(this) { it.toDouble() }
    }

    // --- экспериментальный сбор данных по саже ---
    private var sootPeak = prefs.getFloat("rg_soot_peak", 0f).toDouble()
    private var lastSoot: Double? = null
    private var skipping = false           // подключились посреди прожига: запись не ведём, ждём падения сажи
    private var skipMax = 0.0
    private var awaitDrop = false          // после записи ждём, пока сажа упадёт, и только тогда сбрасываем максимум
    private var followDown = false         // сажа упала вдвое: максимум опускается вместе с ней, пока она не пойдёт вверх
    private var recordedSoot = 0.0
    private val recent = ArrayList<Double>()
    private val log = ArrayList<RegenEntry>().apply { addAll(parseLog(prefs.getString("rg_soot_log", "") ?: "")) }

    private var lastStatus: Int? = null          // статус прожига от блока (DID 0380): 0 нет, не 0 идёт (различие 1 и 2 не подтверждено)
    private var statusSeenAt = 0L
    private var active = false             // сейчас идёт прожиг (по статусу блока)
    private var engineOffSince = 0L        // блок не отвечает или оборотов нет: с какого момента
    private var engineOffHandled = false
    private var hotSince = 0L
    private var coldSince = 0L
    private var endedAt = 0L

    private fun sootLogEnabled() = prefs.getBoolean("exp_soot_log", true)
    private fun statusAvailable(now: Long) = statusSeenAt > 0 && now - statusSeenAt < 60_000

    @Synchronized fun onDist(d: Double) {
        lastSeen = d
        if (awaitReset) {
            if (d < 0.5 * (intervals.lastOrNull() ?: 0.0)) { awaitReset = false; peak = d }
        } else if (d > peak) peak = d
        save()
    }

    @Synchronized fun onSoot(v: Double) {
        if (!sootLogEnabled()) return
        if (v.isNaN() || v < 0 || v > 30) return
        lastSoot = v
        recent.add(v)
        if (recent.size > 3) recent.removeAt(0)
        val smooth = median(recent)
        if (skipping) {
            if (smooth > skipMax) skipMax = smooth
            if (smooth < 0.5 * skipMax) { skipping = false; skipMax = 0.0; sootPeak = smooth; followDown = true }
            save()
            return
        }
        if (awaitDrop && smooth < 0.5 * recordedSoot) {
            awaitDrop = false
            sootPeak = smooth
            followDown = true
        } else if (followDown) {
            // после прожига максимум опускается вместе с сажей; снова растёт, когда сажа поднялась над минимумом
            if (smooth <= sootPeak) sootPeak = smooth
            else if (smooth > sootPeak + 0.1) { followDown = false; sootPeak = smooth }
        } else if (smooth > sootPeak) sootPeak = smooth
        checkTimeout(System.currentTimeMillis())
        save()
    }

    /** Статус прожига от блока (0 нет, не 0 идёт). Включение = статус не 0 STATUS_HOLD_MS подряд, окончание = статус 0 STATUS_END_HOLD_MS подряд. */
    @Synchronized fun onStatus(s: Int) {
        if (!sootLogEnabled() || s < 0 || s > 2) return
        val now = System.currentTimeMillis()
        val newSession = statusSeenAt == 0L || now - statusSeenAt > 60_000
        statusSeenAt = now
        if (lastStatus != s) Timber.i("Статус прожига: %s -> %s", lastStatus, s)
        lastStatus = s
        if (!active) {
            if (s >= 1) {
                if (newSession) {
                    // подключились посреди прожига: максимум сажи неизвестен, запись не ведём
                    active = true; hotSince = 0L; coldSince = 0L
                    skipping = true; skipMax = 0.0; sootPeak = 0.0
                } else {
                    if (hotSince == 0L) hotSince = now
                    else if (now - hotSince >= STATUS_HOLD_MS) startRegen(now)
                }
            } else hotSince = 0L
        } else {
            if (s == 0) {
                if (coldSince == 0L) coldSince = now
                else if (now - coldSince >= STATUS_END_HOLD_MS) endRegen(now)
            } else coldSince = 0L
        }
        checkTimeout(now)
        save()
    }

    /** Обороты двигателя (null — блок не ответил). Двигатель выключен, если ответа или оборотов нет ENGINE_OFF_MS подряд. */
    @Synchronized fun onRpm(rpm: Double?) {
        val now = System.currentTimeMillis()
        if (rpm != null && rpm >= 100) { engineOffSince = 0L; engineOffHandled = false; return }
        if (engineOffSince == 0L) engineOffSince = now
        else if (!engineOffHandled && now - engineOffSince >= ENGINE_OFF_MS) { engineOffHandled = true; engineOff() }
    }

    /** Связь с адаптером закрыта: если блок уже не отвечал, считаем двигатель выключенным. */
    @Synchronized fun onLinkClosed() {
        val now = System.currentTimeMillis()
        if (engineOffSince != 0L && !engineOffHandled && now - engineOffSince >= 5_000L) { engineOffHandled = true; engineOff() }
    }

    /** Двигатель выключен: во время прожига запись «прерван», в пределах 10 минут после его окончания — «завершён». */
    private fun engineOff() {
        Timber.i("Двигатель выключен")
        if (active) {
            closeRecord(if ((lastStatus ?: 0) != 0) RegenState.INTERRUPTED else RegenState.COMPLETE)
            active = false; coldSince = 0L; endedAt = 0L
        }
        hotSince = 0L
        if (skipping) { skipping = false; sootPeak = lastSoot ?: 0.0 }
        save()
    }

    private fun closeRecord(state: Int) {
        val last = log.lastOrNull() ?: return
        if (last.state != RegenState.RUNNING) return
        log[log.lastIndex] = last.copy(state = state)
        if (state == RegenState.INTERRUPTED) {      // сажа после прерванного прожига считается заново от текущего уровня
            sootPeak = lastSoot ?: sootPeak; awaitDrop = false; followDown = true
        }
        Timber.i("Прожиг: запись закрыта, %s", if (state == RegenState.COMPLETE) "завершён" else "прерван")
    }

    private fun endRegen(now: Long) {
        active = false; coldSince = 0L; endedAt = now
        Timber.i("Прожиг: окончание")
        if (skipping) { skipping = false; sootPeak = lastSoot ?: 0.0 }
    }

    private fun startRegen(now: Long) {
        active = true; hotSince = 0L; coldSince = 0L; endedAt = 0L
        val last = log.lastOrNull()
        if (last != null && last.state == RegenState.RUNNING) {
            if (now - last.time < 30 * 60_000L) {
                // статус пропадал и вернулся: это тот же прожиг, новую запись не создаём, берём наибольшую сажу
                if (sootPeak > last.soot) { log[log.lastIndex] = last.copy(soot = sootPeak); recordedSoot = sootPeak }
                Timber.i("Прожиг: продолжение прежней записи")
                return
            }
            log[log.lastIndex] = last.copy(state = RegenState.INTERRUPTED)
        }
        if (sootPeak > 0.3) {
            log.add(RegenEntry(now, sootPeak, lastSeen.takeIf { it > 0 }, RegenState.RUNNING))
            while (log.size > 30) log.removeAt(0)
            recordedSoot = sootPeak
            awaitDrop = true
            followDown = false
            Timber.i("Прожиг: новая запись, сажа %.2f г/л", sootPeak)
        }
    }

    private fun checkTimeout(now: Long) {
        // старые записи «ждём счётчик», за которыми уже есть более поздняя, дальше ждать нечего
        for (i in 0 until log.lastIndex) if (log[i].state == RegenState.RUNNING) log[i] = log[i].copy(state = RegenState.INTERRUPTED)
        val last = log.lastOrNull() ?: return
        if (last.state != RegenState.RUNNING) return
        val noCounter = endedAt > 0 && now - endedAt > 180_000          // прожиг закончился, счётчик не вырос
        val stale = !active && now - last.time > 30 * 60_000L            // давно, и прожига уже нет
        if (noCounter || stale) {
            log[log.lastIndex] = last.copy(state = RegenState.INTERRUPTED)
            endedAt = 0L
        }
    }

    @Synchronized fun onCount(c: Int) {
        val prev = lastCount
        lastCount = c
        if (prev != null && c > prev) {
            val now = System.currentTimeMillis()
            var km: Double? = null
            if (peak > 1.0) {
                intervals.add(peak)
                while (intervals.size > 50) intervals.removeAt(0)
                km = peak
                awaitReset = lastSeen > 0.5 * peak
                peak = if (awaitReset) 0.0 else lastSeen
            }
            val last = log.lastOrNull()
            if (last != null && last.state == RegenState.RUNNING) {
                log[log.lastIndex] = last.copy(state = RegenState.COMPLETE)
                endedAt = 0L
            } else if (sootLogEnabled() && sootPeak > 0.3 && (statusSeenAt == 0L || now - statusSeenAt > 120_000)) {
                // статус прожига недоступен — записываем по росту счётчика
                log.add(RegenEntry(now, sootPeak, km, RegenState.BY_COUNTER))
                while (log.size > 30) log.removeAt(0)
                recordedSoot = sootPeak
                awaitDrop = true
            }
        }
        save()
    }

    @Synchronized fun lastInterval(): Double? = intervals.lastOrNull()
    @Synchronized fun avgInterval(n: Int): Double? = intervals.takeLast(n).takeIf { it.isNotEmpty() }?.average()

    private fun valid() = log.filter { it.state == RegenState.COMPLETE || it.state == RegenState.BY_COUNTER }

    @Synchronized fun lastSootPeak(): Double? = valid().lastOrNull()?.soot
    @Synchronized fun avgSootPeak(n: Int): Double? = valid().takeLast(n).map { it.soot }.takeIf { it.isNotEmpty() }?.average()
    @Synchronized fun currentSootPeak(): Double? = sootPeak.takeIf { it > 0 }
    @Synchronized fun currentSoot(): Double? = lastSoot
    @Synchronized fun regenActive(): Boolean = active
    /** Статус от блока (0/1/2), если он сейчас приходит, иначе null. */
    @Synchronized fun liveStatus(): Int? = if (statusAvailable(System.currentTimeMillis())) lastStatus else null
    @Synchronized fun history(): List<RegenEntry> = ArrayList(log)
    @Synchronized fun clearSootLog() {
        log.clear(); sootPeak = 0.0; awaitDrop = false; followDown = false; recent.clear()
        save()
    }

    private fun median(l: List<Double>): Double {
        val s = l.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun parseLog(text: String): List<RegenEntry> =
        text.split(";").filter { it.isNotBlank() }.mapNotNull { rec ->
            val p = rec.split("|")
            val t = p.getOrNull(0)?.toLongOrNull()
            val s = p.getOrNull(1)?.toDoubleOrNull()
            val st = p.getOrNull(3)?.toIntOrNull() ?: RegenState.BY_COUNTER   // старые записи были по счётчику
            val forced = false   // метка «принудительный» больше не используется
            if (t == null || s == null) null else RegenEntry(t, s, p.getOrNull(2)?.toDoubleOrNull(), st, forced)
        }

    private fun save() {
        prefs.edit()
            .putInt("rg_count", lastCount ?: -1)
            .putFloat("rg_peak", peak.toFloat())
            .putFloat("rg_seen", lastSeen.toFloat())
            .putString("rg_hist", intervals.joinToString(","))
            .putFloat("rg_soot_peak", sootPeak.toFloat())
            .putString("rg_soot_log", log.joinToString(";") { "${it.time}|${it.soot}|${it.km ?: ""}|${it.state}|${if (it.forced) 1 else 0}" })
            .apply()
    }

    companion object {
        private const val ENGINE_OFF_MS = 10_000L    // блок не отвечает или оборотов нет
        private const val STATUS_HOLD_MS = 10_000L   // статус блока не 0
        private const val STATUS_END_HOLD_MS = 600_000L   // статус блока 0: прожиг закончился через 10 минут
        @Volatile private var inst: RegenTracker? = null
        fun get(prefs: SharedPreferences): RegenTracker =
            inst ?: synchronized(this) { inst ?: RegenTracker(prefs).also { inst = it } }
    }
}
