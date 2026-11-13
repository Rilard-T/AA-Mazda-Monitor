package com.aatorque.obd

import android.content.SharedPreferences
import java.io.IOException
import timber.log.Timber

/** Опрос блоков через ELM327: инициализация, смена заголовков, кэш, разбор ответов. */
class ObdEngine(private val link: ObdLink, private val prefs: SharedPreferences) {
    val tracker = RegenTracker.get(prefs)

    private class Sample(val t: Long, val v: Double?)
    private val cache = HashMap<String, Sample>()
    /** Последнее удачное значение медленных датчиков: при единичном промахе показываем его, а не 0. */
    private val lastGood = HashMap<String, Sample>()
    private val lastWarn = HashMap<String, Long>()
    @Volatile private var lastRaw = ""
    private val HOLD_SLOW_MS = 30_000L
    private val supported = HashSet<Int>()      // PID режима 01, о которых заявил блок двигателя
    private var curHdr = ""
    /** Просьба адаптеру вернуть ответ сразу после первого сообщения (последняя hex-цифра «1» в запросе). */
    private var useCount = prefs.getBoolean("fast_adapter", true)

    /** Минимальный интервал между опросами одного быстрого датчика (мс), из настроек. */
    private fun fastMs(): Long = prefs.getString("poll_fast_ms", "100")?.toLongOrNull() ?: 100L
    /** Интервал для медленных датчиков (сажа, АКПП, прожиги), из настроек. */
    private fun slowMs(): Long = prefs.getString("poll_slow_ms", "3000")?.toLongOrNull() ?: 3000L
    private var bg: Thread? = null
    @Volatile private var closed = false

    /** Подряд идущие сбои связи; сервис переподключается, когда их набирается 5. */
    @Volatile var failures = 0
        private set

    /** Порог сажи (г/л) для расчёта процента заполнения; 0 — не задан. */
    fun dpfFullGl(): Double {
        // экспериментально: порог по наблюдениям (среднее сажи перед 3 последними прожигами)
        if (prefs.getBoolean("exp_auto_threshold", false)) {
            tracker.avgSootPeak(3)?.let { if (it > 0) return it }
        }
        val p = prefs.getFloat("dpf_full_gl", 0f).toDouble()
        return if (p > 0) p else ObdConfig.DPF_FULL_GL
    }

    @Synchronized
    fun init() {
        curHdr = ""
        Thread.sleep(400)                                   // клонам нужна пауза после соединения
        runCatching { link.command("ATZ", 3000) }
        for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", if (useCount) "ATAT2" else "ATAT1", "ATSP6")) link.command(c, 1500)
        var r = parse(send("7E0", "0100", 4000), "4100")
        if (r == null) {                                   // не CAN 11/500 — автопоиск протокола
            link.command("ATSP0", 1500); curHdr = ""
            r = parse(send("7E0", "0100", 8000), "4100")
        }
        if (r == null) throw IOException("Блок двигателя не отвечает. Включите зажигание.")
        readSupported(r)
        Timber.i("Режим 01, заявлено блоком: %s", supported.sorted().joinToString(",") { "%02X".format(it) })
        failures = 0
        readId("33")                                       // барометр нужен для расчёта наддува
        bg = Thread(::backgroundLoop, "obd-regen").also { it.isDaemon = true; it.start() }
    }

    fun close() {
        closed = true
        tracker.onLinkClosed()
        bg?.interrupt()
        link.close()
    }

    /** Счётчик и пробег прожига читаем сами, чтобы журнал не зависел от того, что выведено на экран. */
    private fun backgroundLoop() {
        try {
            var n = 0
            while (!closed) {
                // принудительное чтение (мимо кэша): температура и сажа каждые 5 с, счётчик и пробег каждые 10 с
                readFresh("ff9006"); readFresh("ff9003")
                readFresh("0c")   // обороты: по ним (и по молчанию блока) журнал прожигов определяет, что двигатель выключен
                if (n % 2 == 0) { readFresh("ff9001"); readFresh("ff9002") }
                if (n % 6 == 0) { stateLog(); diagLog() }   // только журнал: сырые ответы блока на 01 78 / 7A / 8B / 73
                n++
                Thread.sleep(5_000)
            }
        } catch (_: InterruptedException) { }
    }

    /** Сырой ответ блока двигателя на запрос режима 01 (только для журнала). */
    @Synchronized
    private fun diagRaw(req: String): String = try {
        // без цифры-счётчика: иначе адаптер отдаёт только первый кадр многокадрового ответа
        send("7E0", req, 2500)
    } catch (e: IOException) { failures++; "ошибка связи: " + (e.message ?: "") }

    /** Раз в 30 с пишет в журнал строку состояния (значения берутся из кэша, запросов к блоку нет). */
    private fun stateLog() {
        val now = System.currentTimeMillis()
        fun v(id: String): Double? = synchronized(this) { cache[id]?.takeIf { now - it.t < 20_000L }?.v }
        fun f(x: Double?, d: Int = 0): String = x?.let { "%.${d}f".format(java.util.Locale.US, it) } ?: "—"
        val soot = tracker.currentSoot()
        val full = dpfFullGl()
        val dpf = if (soot != null && full > 0) soot / full * 100.0 else null
        Timber.i(
            "Состояние %s: сажа %s г/л (макс %s), DPF %s%% (порог %s), статус %s, счётчик %s, пробег %s км, обороты %s, скорость %s, ОЖ %s, АКПП %s, масло %s",
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(now)),
            f(soot, 2), f(tracker.currentSootPeak(), 2), f(dpf, 1), f(full, 2),
            f(v("ff9006")), f(v("ff9001")), f(v("ff9002")), f(v("0c")), f(v("0d")), f(v("05")), f(v("ff9005")), f(v("5c")),
        )
    }

    /** Раз в 30 с пишет в журнал ответы блока на параметры, которые могут пригодиться для сажевого. На экраны не выводится. */
    private fun diagLog() {
        for (pid in listOf("78", "7A", "8B", "73")) {
            val raw = diagRaw("01$pid")
            Timber.i("Диагностика 01%s: %s", pid, raw.replace(Regex("\\s+"), " ").trim().take(160))
            if (pid == "78") parse(raw, "4178")?.takeIf { it.size >= 9 }?.let { b ->
                val t = (0 until 4).joinToString(" ") { i -> "%.1f".format((b[1 + 2 * i] * 256 + b[2 + 2 * i]) / 10.0 - 40.0) }
                Timber.i("Диагностика 0178: флаги %02X, температуры %s °C", b[0], t)
            }
        }
    }

    fun listIds(): Array<String> = MazdaPids.all
        .filter { !it.isStandard || supported.isEmpty() || supported.contains(it.stdPid) }
        .map { it.id }.toTypedArray()

    fun value(id: String): Double? = MazdaPids.byId(id)?.let { read(it) }
    fun readId(id: String): Double? = value(id)
    private fun readFresh(id: String): Double? = MazdaPids.byId(id)?.let { read(it, true) }

    @Synchronized
    fun read(p: PidDef, force: Boolean = false): Double? {
        val now = System.currentTimeMillis()
        val ttl = if (p.slow) slowMs() else fastMs()
        if (!force) cache[p.id]?.let { if (now - it.t < ttl) return it.v }
        var failed = false
        var v = try { fetch(p) } catch (e: IOException) { failures++; failed = true; lastRaw = e.message ?: "ошибка связи"; null }
        if (v == null && !failed && p.virtual == null && p.header != "7E0") {   // блок АКПП иногда молчит: один быстрый повтор
            v = try { fetch(p) } catch (e: IOException) { failures++; failed = true; lastRaw = e.message ?: "ошибка связи"; null }
        }
        val t = System.currentTimeMillis()
        var shown: Double? = v
        if (v != null) {
            if (p.slow) lastGood[p.id] = Sample(t, v)
        } else if (p.slow && p.virtual == null) {
            warnMiss(p, t)
            val g = lastGood[p.id]
            if (g != null && t - g.t < HOLD_SLOW_MS) shown = g.v
        } else if (p.virtual == null && (p.id == "7c" || p.id == "7a")) {
            warnMiss(p, t)   // диагностика 7C/7A: сырой ответ блока
        }
        cache[p.id] = Sample(t, shown)
        if (p.id == "0c") tracker.onRpm(v)   // v == null: блок не ответил
        if (v != null) when (p.id) {
            "ff9001" -> tracker.onCount(v.toInt())
            "ff9002" -> tracker.onDist(v)
            "ff9003" -> tracker.onSoot(v)
            "ff9006" -> tracker.onStatus(v.toInt())
        }
        return shown
    }

    /** Пишет в журнал причину промаха (ответ адаптера), не чаще раза в 10 с на датчик. */
    private fun warnMiss(p: PidDef, now: Long) {
        if (now - (lastWarn[p.id] ?: 0L) < 10_000L) return
        lastWarn[p.id] = now
        Timber.w("Нет данных %s (блок %s): %s", p.id, p.header, lastRaw.replace(Regex("\\s+"), " ").take(120))
    }

    private fun fetch(p: PidDef): Double? {
        p.virtual?.let { return it(this) }
        val req = p.request ?: return null
        val prefix = (if (req.startsWith("01")) "41" else "62") + req.substring(2)
        var text = send(p.header, if (useCount) req + "1" else req, 1500)
        if (useCount && text.contains('?')) {      // адаптер не понял сокращённую форму — работаем без неё
            useCount = false
            text = send(p.header, req, 1500)
        }
        lastRaw = text
        failures = 0
        val b = parse(text, prefix) ?: return null
        return p.decode?.invoke(b)
    }

    private fun send(hdr: String, cmd: String, timeoutMs: Long): String {
        if (hdr != curHdr) { link.command("ATSH$hdr", 1000); curHdr = hdr }
        return link.command(cmd, timeoutMs)
    }

    private fun readSupported(first: IntArray) {
        supported.clear()
        var bits: IntArray? = first
        var base = 0
        while (bits != null && bits.size >= 4) {
            for (i in 0 until 32) if ((bits[i shr 3] and (0x80 shr (i and 7))) != 0) supported.add(base + i + 1)
            if (!supported.contains(base + 32) || base >= 0xA0) break
            base += 32
            val h = "%02X".format(base)
            bits = parse(send("7E0", "01$h", 2000), "41$h")
        }
    }

    /** Байты ответа после префикса (например «410C» или «620434»); null, если ответа нет. */
    private fun parse(text: String?, prefix: String): IntArray? {
        if (text.isNullOrBlank()) return null
        val lines = text.uppercase().split(Regex("[\\r\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val multi = lines.any { Regex("^[0-9A-F]\\s*:").containsMatchIn(it) }
        val sb = StringBuilder()
        for (l in lines) {
            if (multi) {
                Regex("^[0-9A-F]\\s*:([0-9A-F ]*)$").find(l)?.let { sb.append(it.groupValues[1]) }
            } else if (Regex("^[0-9A-F ]+$").matches(l)) sb.append(l)
        }
        val hex = sb.toString().replace(" ", "")
        val i = hex.indexOf(prefix.uppercase())
        if (i < 0) return null
        val rest = hex.substring(i + prefix.length)
        return IntArray(rest.length / 2) { rest.substring(it * 2, it * 2 + 2).toInt(16) }
    }
}
