#!/usr/bin/env python3
"""Релиз 0.7.5: все патчи одним запуском.

  - журнал прожигов (статус блока, 10 минут простоя, слияние дублей, максимум после прожига,
    выключение двигателя: во время прожига «прерван», в пределах 10 минут после окончания «завершён»);
  - диагностика сажевого в журнале (01 78 / 7A / 8B / 73), без вывода на приборы;
  - удаление параметра 7C и выбора способа отслеживания прожига;
  - тема «Mazda gen4» первой в диалоге выбора.

Запуск из корня репозитория:  python3 patch_release_075.py
Повторный запуск безопасен.
"""
import sys

SCRIPTS = [
    ('patch_regen_fix.py', r'''#!/usr/bin/env python3
"""Журнал прожигов (версия 0.7.5): правки RegenTracker.kt.

  1. Прожиг начинается по статусу блока (22 0380), прожиг заканчивается, когда статус 0 держится
     10 минут (было 10 секунд). Выбор способа отслеживания и температурный триггер убраны.
  2. Перезапуск прожига, пока прежняя запись ещё «ждём счётчик» (в пределах 30 минут), не создаёт
     новую запись, а продолжает прежнюю (сажа берётся наибольшая).
  3. Двигатель выключен (нет ответа блока или оборотов 10 секунд подряд):
       - во время прожига (статус не 0) запись «прерван», независимо от остатка сажи;
       - в пределах 10 минут после окончания прожига (статус 0 меньше 10 минут) запись «завершён».
     После прерванного прожига «максимум с прошлого прожига» считается заново от текущей сажи.
  4. «Максимум с прошлого прожига» после падения сажи вдвое опускается вместе с сажей и снова
     растёт, когда сажа поднялась над минимумом больше чем на 0,1 г/л.
  5. Исключение «принудительных» прожигов из среднего и порога убрано; метка «принудительный»
     больше не ставится (у старых записей сбрасывается при чтении).
  6. Старые записи «ждём счётчик», за которыми есть более поздняя, закрываются как «прерван».
  7. В журнал приложения пишутся смены статуса прожига и события записи.

Запуск из корня репозитория:  python3 patch_regen_fix.py
Повторный запуск безопасен.
"""
import sys
from pathlib import Path

KT = Path("app/src/main/java/com/aatorque/obd/RegenTracker.kt")
if not KT.exists():
    sys.exit("Не найден " + str(KT) + ". Запускайте скрипт из корня репозитория.")

s = KT.read_text(encoding="utf-8")
if "STATUS_END_HOLD_MS" in s:
    sys.exit("Патч уже применён, ничего не меняю.")

def rep(old, new):
    global s
    n = s.count(old)
    if n != 1:
        sys.exit("Не найден ровно один фрагмент (найдено %d): %r" % (n, old[:90]))
    s = s.replace(old, new)

def cut(start, end, must):
    """Удаляет s[start:end), где start и end — маркеры; в вырезаемом должен быть текст must."""
    global s
    if s.count(start) != 1 or s.count(end) != 1:
        sys.exit("Не нашёл границы блока: %r" % start[:60])
    a, b = s.index(start), s.index(end)
    if not (a < b) or must not in s[a:b]:
        sys.exit("Блок для удаления выглядит иначе, чем ожидалось: %r" % start[:60])
    s = s[:a] + s[b:]

rep("import android.content.SharedPreferences\n",
    "import android.content.SharedPreferences\nimport timber.log.Timber\n")

# ---------- комментарии ----------
rep("    const val RUNNING = 0      // прожиг начался (по температуре), ещё не завершён",
    "    const val RUNNING = 0      // прожиг начался (по статусу блока), ещё не завершён")
rep("    const val INTERRUPTED = 2  // счётчик не вырос: прожиг прерван или это был ложный нагрев",
    "    const val INTERRUPTED = 2  // прожиг прерван (двигатель выключили во время прожига или счётчик не вырос)")
rep("    const val BY_COUNTER = 3   // температура недоступна, запись сделана по росту счётчика",
    "    const val BY_COUNTER = 3   // статус прожига недоступен, запись сделана по росту счётчика")
rep(" * Экспериментально: запись сажи делается в момент ВКЛЮЧЕНИЯ прожига (температура перед сажевым\n"
    " * держится выше порога), по максимуму сажи, накопленному до этого момента.",
    " * Экспериментально: запись сажи делается в момент ВКЛЮЧЕНИЯ прожига (статус блока не 0),\n"
    " * по максимуму сажи, накопленному до этого момента.")

# ---------- поля ----------
rep("    private var awaitDrop = false          // после записи ждём, пока сажа упадёт, и только тогда сбрасываем максимум\n",
    "    private var awaitDrop = false          // после записи ждём, пока сажа упадёт, и только тогда сбрасываем максимум\n"
    "    private var followDown = false         // сажа упала вдвое: максимум опускается вместе с ней, пока она не пойдёт вверх\n")
rep("// статус прожига от блока (DID 0380): 0 нет, 1 обычный, 2 принудительный",
    "// статус прожига от блока (DID 0380): 0 нет, не 0 идёт (различие 1 и 2 не подтверждено)")
rep("    private var lastTemp: Double? = null\n"
    "    private var tempSeenAt = 0L\n"
    "    private var active = false             // сейчас идёт прожиг (по температуре)\n",
    "    private var active = false             // сейчас идёт прожиг (по статусу блока)\n"
    "    private var engineOffSince = 0L        // блок не отвечает или оборотов нет: с какого момента\n"
    "    private var engineOffHandled = false\n")
rep("    private fun useStatus() = prefs.getString(\"exp_regen_source\", \"auto\") != \"temp\"\n"
    "    private fun statusAvailable(now: Long) = useStatus() && statusSeenAt > 0 && now - statusSeenAt < 60_000\n"
    "\n"
    "    private fun startTemp() = prefs.getString(\"exp_regen_temp\", \"450\")?.toDoubleOrNull() ?: 450.0\n",
    "    private fun statusAvailable(now: Long) = statusSeenAt > 0 && now - statusSeenAt < 60_000\n")

# ---------- onSoot ----------
rep("if (smooth < 0.5 * skipMax) { skipping = false; skipMax = 0.0; sootPeak = smooth }",
    "if (smooth < 0.5 * skipMax) { skipping = false; skipMax = 0.0; sootPeak = smooth; followDown = true }")
rep("        if (awaitDrop && smooth < 0.5 * recordedSoot) {\n"
    "            awaitDrop = false\n"
    "            sootPeak = smooth\n"
    "        } else if (smooth > sootPeak) sootPeak = smooth\n",
    "        if (awaitDrop && smooth < 0.5 * recordedSoot) {\n"
    "            awaitDrop = false\n"
    "            sootPeak = smooth\n"
    "            followDown = true\n"
    "        } else if (followDown) {\n"
    "            // после прожига максимум опускается вместе с сажей; снова растёт, когда сажа поднялась над минимумом\n"
    "            if (smooth <= sootPeak) sootPeak = smooth\n"
    "            else if (smooth > sootPeak + 0.1) { followDown = false; sootPeak = smooth }\n"
    "        } else if (smooth > sootPeak) sootPeak = smooth\n")

# ---------- onTemp удаляется целиком ----------
cut("    /** Температура перед сажевым фильтром (°C).",
    "    /** Статус прожига от блока (0 нет, 1 обычный, 2 принудительный).",
    "fun onTemp")

# ---------- onStatus ----------
rep("/** Статус прожига от блока (0 нет, 1 обычный, 2 принудительный). Включение = статус не 0 STATUS_HOLD_MS подряд. */",
    "/** Статус прожига от блока (0 нет, не 0 идёт). Включение = статус не 0 STATUS_HOLD_MS подряд, окончание = статус 0 STATUS_END_HOLD_MS подряд. */")
rep("        statusSeenAt = now\n        lastStatus = s\n        if (!useStatus()) return\n",
    "        statusSeenAt = now\n"
    "        if (lastStatus != s) Timber.i(\"Статус прожига: %s -> %s\", lastStatus, s)\n"
    "        lastStatus = s\n")
rep("else if (now - hotSince >= STATUS_HOLD_MS) startRegen(now, s == 2)",
    "else if (now - hotSince >= STATUS_HOLD_MS) startRegen(now)")
rep("            if (s == 2) markForced()\n", "")
rep("else if (now - coldSince >= STATUS_HOLD_MS) endRegen(now)",
    "else if (now - coldSince >= STATUS_END_HOLD_MS) endRegen(now)")

# ---------- двигатель выключен, окончание и старт прожига ----------
rep("    private fun endRegen(now: Long) {\n        active = false; coldSince = 0L; endedAt = now\n",
    "    /** Обороты двигателя (null — блок не ответил). Двигатель выключен, если ответа или оборотов нет ENGINE_OFF_MS подряд. */\n"
    "    @Synchronized fun onRpm(rpm: Double?) {\n"
    "        val now = System.currentTimeMillis()\n"
    "        if (rpm != null && rpm >= 100) { engineOffSince = 0L; engineOffHandled = false; return }\n"
    "        if (engineOffSince == 0L) engineOffSince = now\n"
    "        else if (!engineOffHandled && now - engineOffSince >= ENGINE_OFF_MS) { engineOffHandled = true; engineOff() }\n"
    "    }\n"
    "\n"
    "    /** Связь с адаптером закрыта: если блок уже не отвечал, считаем двигатель выключенным. */\n"
    "    @Synchronized fun onLinkClosed() {\n"
    "        val now = System.currentTimeMillis()\n"
    "        if (engineOffSince != 0L && !engineOffHandled && now - engineOffSince >= 5_000L) { engineOffHandled = true; engineOff() }\n"
    "    }\n"
    "\n"
    "    /** Двигатель выключен: во время прожига запись «прерван», в пределах 10 минут после его окончания — «завершён». */\n"
    "    private fun engineOff() {\n"
    "        Timber.i(\"Двигатель выключен\")\n"
    "        if (active) {\n"
    "            closeRecord(if ((lastStatus ?: 0) != 0) RegenState.INTERRUPTED else RegenState.COMPLETE)\n"
    "            active = false; coldSince = 0L; endedAt = 0L\n"
    "        }\n"
    "        hotSince = 0L\n"
    "        if (skipping) { skipping = false; sootPeak = lastSoot ?: 0.0 }\n"
    "        save()\n"
    "    }\n"
    "\n"
    "    private fun closeRecord(state: Int) {\n"
    "        val last = log.lastOrNull() ?: return\n"
    "        if (last.state != RegenState.RUNNING) return\n"
    "        log[log.lastIndex] = last.copy(state = state)\n"
    "        if (state == RegenState.INTERRUPTED) {      // сажа после прерванного прожига считается заново от текущего уровня\n"
    "            sootPeak = lastSoot ?: sootPeak; awaitDrop = false; followDown = true\n"
    "        }\n"
    "        Timber.i(\"Прожиг: запись закрыта, %s\", if (state == RegenState.COMPLETE) \"завершён\" else \"прерван\")\n"
    "    }\n"
    "\n"
    "    private fun endRegen(now: Long) {\n        active = false; coldSince = 0L; endedAt = now\n"
    "        Timber.i(\"Прожиг: окончание\")\n")
rep("    private fun markForced() {\n"
    "        val last = log.lastOrNull() ?: return\n"
    "        if (last.state == RegenState.RUNNING && !last.forced) log[log.lastIndex] = last.copy(forced = true)\n"
    "    }\n\n", "")
rep("    private fun startRegen(now: Long, forced: Boolean = false) {\n"
    "        active = true; hotSince = 0L; coldSince = 0L; endedAt = 0L\n"
    "        if (sootPeak > 0.3) {\n"
    "            log.add(RegenEntry(now, sootPeak, lastSeen.takeIf { it > 0 }, RegenState.RUNNING, forced))\n"
    "            while (log.size > 30) log.removeAt(0)\n"
    "            recordedSoot = sootPeak\n"
    "            awaitDrop = true\n"
    "        }\n"
    "    }\n",
    "    private fun startRegen(now: Long) {\n"
    "        active = true; hotSince = 0L; coldSince = 0L; endedAt = 0L\n"
    "        val last = log.lastOrNull()\n"
    "        if (last != null && last.state == RegenState.RUNNING) {\n"
    "            if (now - last.time < 30 * 60_000L) {\n"
    "                // статус пропадал и вернулся: это тот же прожиг, новую запись не создаём, берём наибольшую сажу\n"
    "                if (sootPeak > last.soot) { log[log.lastIndex] = last.copy(soot = sootPeak); recordedSoot = sootPeak }\n"
    "                Timber.i(\"Прожиг: продолжение прежней записи\")\n"
    "                return\n"
    "            }\n"
    "            log[log.lastIndex] = last.copy(state = RegenState.INTERRUPTED)\n"
    "        }\n"
    "        if (sootPeak > 0.3) {\n"
    "            log.add(RegenEntry(now, sootPeak, lastSeen.takeIf { it > 0 }, RegenState.RUNNING))\n"
    "            while (log.size > 30) log.removeAt(0)\n"
    "            recordedSoot = sootPeak\n"
    "            awaitDrop = true\n"
    "            followDown = false\n"
    "            Timber.i(\"Прожиг: новая запись, сажа %.2f г/л\", sootPeak)\n"
    "        }\n"
    "    }\n")

# ---------- checkTimeout, onCount ----------
rep("    private fun checkTimeout(now: Long) {\n        val last = log.lastOrNull() ?: return\n",
    "    private fun checkTimeout(now: Long) {\n"
    "        // старые записи «ждём счётчик», за которыми уже есть более поздняя, дальше ждать нечего\n"
    "        for (i in 0 until log.lastIndex) if (log[i].state == RegenState.RUNNING) log[i] = log[i].copy(state = RegenState.INTERRUPTED)\n"
    "        val last = log.lastOrNull() ?: return\n")
rep("            } else if (sootLogEnabled() && sootPeak > 0.3 && (tempSeenAt == 0L || now - tempSeenAt > 120_000) &&\n"
    "                (statusSeenAt == 0L || now - statusSeenAt > 120_000)) {\n"
    "                // температура недоступна — записываем по росту счётчика\n",
    "            } else if (sootLogEnabled() && sootPeak > 0.3 && (statusSeenAt == 0L || now - statusSeenAt > 120_000)) {\n"
    "                // статус прожига недоступен — записываем по росту счётчика\n")

# ---------- valid(), clear, parse, currentTemp, константы ----------
rep("private fun valid() = log.filter { (it.state == RegenState.COMPLETE || it.state == RegenState.BY_COUNTER) && !it.forced }",
    "private fun valid() = log.filter { it.state == RegenState.COMPLETE || it.state == RegenState.BY_COUNTER }")
rep("    @Synchronized fun currentTemp(): Double? = lastTemp\n", "")
rep("        log.clear(); sootPeak = 0.0; awaitDrop = false; recent.clear()\n",
    "        log.clear(); sootPeak = 0.0; awaitDrop = false; followDown = false; recent.clear()\n")
rep('            val forced = p.getOrNull(4) == "1"\n',
    "            val forced = false   // метка «принудительный» больше не используется\n")
rep("        private const val HOLD_MS = 20_000L          // температура выше порога\n",
    "        private const val ENGINE_OFF_MS = 10_000L    // блок не отвечает или оборотов нет\n")
rep("        private const val STATUS_HOLD_MS = 10_000L   // статус блока не 0\n",
    "        private const val STATUS_HOLD_MS = 10_000L   // статус блока не 0\n"
    "        private const val STATUS_END_HOLD_MS = 600_000L   // статус блока 0: прожиг закончился через 10 минут\n")

KT.write_text(s, encoding="utf-8")
print("Готово: изменён", KT)
'''),
    ('patch_dpf_temp_log.py', r'''#!/usr/bin/env python3
"""ObdEngine.kt (версия 0.7.5): диагностика сажевого в журнале и определение выключенного двигателя.

Нужна версия после patch_dpf_diag.py (0.7.4). Что делает:
  1. Раз в 30 секунд пишет в журнал сырые ответы блока на 01 78 (температуры выхлопных газов,
     банк 1), 01 7A (давление на сажевом), 01 8B (состояние дожига), 01 73 (давление выхлопа).
     Для 01 78 дополнительно расшифровывает до четырёх температур. На экраны не выводится.
  2. Параметр 7C (температура перед сажевым) больше не опрашивается и не передаётся в журнал
     прожигов: на двигателях 1.8 его нет.
  3. Раз в 5 секунд читает обороты (01 0C) и передаёт их журналу прожигов: по ним и по молчанию
     блока определяется, что двигатель выключен. При закрытии связи журналу тоже сообщается.

Запуск из корня репозитория:  python3 patch_dpf_temp_log.py
Повторный запуск безопасен.
"""
import sys
from pathlib import Path

KT = Path("app/src/main/java/com/aatorque/obd/ObdEngine.kt")
if not KT.exists():
    sys.exit("Не найден " + str(KT) + ". Запускайте скрипт из корня репозитория.")
s = KT.read_text(encoding="utf-8")
if "diagLog" in s:
    sys.exit("Патч уже применён, ничего не меняю.")

def rep(old, new):
    global s
    n = s.count(old)
    if n != 1:
        sys.exit("Не найден ровно один фрагмент (найдено %d): %r" % (n, old[:90]))
    s = s.replace(old, new)

# фоновый цикл: без 7C, с оборотами и диагностикой
rep('                readFresh("ff9006"); readFresh("7c"); readFresh("ff9003")\n'
    '                if (n % 2 == 0) { readFresh("ff9001"); readFresh("ff9002") }\n',
    '                readFresh("ff9006"); readFresh("ff9003")\n'
    '                readFresh("0c")   // обороты: по ним (и по молчанию блока) журнал прожигов определяет, что двигатель выключен\n'
    '                if (n % 2 == 0) { readFresh("ff9001"); readFresh("ff9002") }\n'
    '                if (n % 6 == 0) diagLog()   // только журнал: сырые ответы блока на 01 78 / 7A / 8B / 73\n')

# обороты и результат опроса — журналу прожигов; 7C больше не передаётся
rep('            "7c" -> tracker.onTemp(v)\n', '')
rep('        if (v != null) when (p.id) {\n',
    '        if (p.id == "0c") tracker.onRpm(v)   // v == null: блок не ответил\n'
    '        if (v != null) when (p.id) {\n')

# закрытие связи
rep("    fun close() {\n        closed = true\n",
    "    fun close() {\n        closed = true\n        tracker.onLinkClosed()\n")

# диагностика
rep("    fun listIds(): Array<String> = MazdaPids.all\n",
    "    /** Сырой ответ блока двигателя на запрос режима 01 (только для журнала). */\n"
    "    @Synchronized\n"
    "    private fun diagRaw(req: String): String = try {\n"
    "        send(\"7E0\", if (useCount) req + \"1\" else req, 1500)\n"
    "    } catch (e: IOException) { failures++; \"ошибка связи: \" + (e.message ?: \"\") }\n"
    "\n"
    "    /** Раз в 30 с пишет в журнал ответы блока на параметры, которые могут пригодиться для сажевого. На экраны не выводится. */\n"
    "    private fun diagLog() {\n"
    "        for (pid in listOf(\"78\", \"7A\", \"8B\", \"73\")) {\n"
    "            val raw = diagRaw(\"01$pid\")\n"
    "            Timber.i(\"Диагностика 01%s: %s\", pid, raw.replace(Regex(\"\\\\s+\"), \" \").trim().take(160))\n"
    "            if (pid == \"78\") parse(raw, \"4178\")?.takeIf { it.size >= 9 }?.let { b ->\n"
    "                val t = (0 until 4).joinToString(\" \") { i -> \"%.1f\".format((b[1 + 2 * i] * 256 + b[2 + 2 * i]) / 10.0 - 40.0) }\n"
    "                Timber.i(\"Диагностика 0178: флаги %02X, температуры %s °C\", b[0], t)\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "\n"
    "    fun listIds(): Array<String> = MazdaPids.all\n")

KT.write_text(s, encoding="utf-8")
print("Готово: изменён", KT)
'''),
    ('patch_remove_temp.py', r'''#!/usr/bin/env python3
"""Убирает параметр «Температура перед сажевым» (7C) из списка параметров (Pids.kt).

У двигателей 1.8 этого датчика нет (он есть у 1.5), параметр только путал. Если он был выбран
на каком-то приборе или плитке, там будет «нет данных»: выберите другой параметр.

Запуск из корня репозитория:  python3 patch_remove_temp.py
Повторный запуск безопасен.
"""
import sys
from pathlib import Path

KT = Path("app/src/main/java/com/aatorque/obd/Pids.kt")
if not KT.exists():
    sys.exit("Не найден " + str(KT) + ". Запускайте скрипт из корня репозитория.")
s = KT.read_text(encoding="utf-8")
old = ('        PidDef("7c", "Температура перед сажевым", "DPF T", "°C", 0.0, 800.0, request = "017C",\n'
       '            decode = { b -> if ((b.u(0) and 1) != 0) (b.u(1) * 256 + b.u(2)) / 10.0 - 40.0 else null }),\n')
if '"7c"' not in s:
    sys.exit("Патч уже применён, ничего не меняю.")
if s.count(old) != 1:
    sys.exit("Не найден ровно один фрагмент с параметром 7C (найдено %d)." % s.count(old))
KT.write_text(s.replace(old, ""), encoding="utf-8")
print("Готово: изменён", KT)
'''),
    ('patch_experimental_ui.py', r'''#!/usr/bin/env python3
"""Экран «Экспериментальные функции» (ObdExperimental.kt): убирает выбор способа отслеживания прожига.

  - удалены настройки «Чем определять включение прожига» и «Температура начала прожига»;
  - удалена строка «Температура перед сажевым» в блоке «Данные»;
  - строка «Прожиг» показывает «не идёт» / «идёт (статус блока: N)» / «нет данных»;
  - в журнале больше нет пометки «принудительный»;
  - обновлён текст раздела «Зачем это нужно».

Запуск из корня репозитория:  python3 patch_experimental_ui.py
Повторный запуск безопасен.
"""
import sys
from pathlib import Path

KT = Path("app/src/main/java/com/aatorque/obd/ObdExperimental.kt")
if not KT.exists():
    sys.exit("Не найден " + str(KT) + ". Запускайте скрипт из корня репозитория.")
s = KT.read_text(encoding="utf-8")
if "exp_regen_source" not in s:
    sys.exit("Патч уже применён, ничего не меняю.")

def rep(old, new):
    global s
    n = s.count(old)
    if n != 1:
        sys.exit("Не найден ровно один фрагмент (найдено %d): %r" % (n, old[:90]))
    s = s.replace(old, new)

rep("import androidx.preference.ListPreference\n", "")
rep("    private var tempPref: Preference? = null\n", "")

# два выбора в блоке «Сбор данных»
a = s.index("        val src = ListPreference(ctx)\n")
b = s.index("        val auto = SwitchPreferenceCompat(ctx)\n")
cutout = s[a:b]
if "exp_regen_source" not in cutout or "exp_regen_temp" not in cutout or "collect.addPreference(temp)" not in cutout:
    sys.exit("Блок настроек выглядит иначе, чем ожидалось.")
s = s[:a] + s[b:]

rep('        tempPref = info(data, "Температура перед сажевым", "—")\n', "")
rep('        tempPref?.summary = tracker.currentTemp()?.let { String.format(Locale.US, "%.0f °C", it) } ?: "нет данных"\n', "")
rep('        statePref?.summary = when (val s = tracker.liveStatus()) {\n'
    '            null -> (if (tracker.regenActive()) "идёт" else "не идёт") + " (по температуре)"\n'
    '            0 -> "не идёт (статус блока)"\n'
    '            1 -> "идёт, обычный (статус блока)"\n'
    '            else -> "идёт, принудительный (статус блока: $s)"\n'
    '        }\n',
    '        statePref?.summary = when (val s = tracker.liveStatus()) {\n'
    '            null -> "нет данных (статус блока недоступен)"\n'
    '            0 -> "не идёт"\n'
    '            else -> "идёт (статус блока: $s)"\n'
    '        }\n')
rep('", " + stateText(it.state) +\n                (if (it.forced) ", принудительный" else "")\n',
    '", " + stateText(it.state)\n')
rep('                    "Запись делается в момент включения прожига: когда блок сообщает статус 1 или 2, а если статус " +\n'
    '                    "недоступен, когда температура перед сажевым держится выше порога 20 секунд. В журнал попадает " +\n'
    '                    "максимум сажи до этого момента.\\n\\n" +\n'
    '                    "Прожиг помечается «завершён» (счётчик вырос) или «прерван» (не вырос за 3 минуты). В среднем " +\n'
    '                    "участвуют только завершённые обычные прожиги, принудительные записываются, но не учитываются.\\n\\n" +\n',
    '                    "Запись делается в момент включения прожига, когда блок сообщает ненулевой статус прожига. " +\n'
    '                    "В журнал попадает максимум сажи до этого момента. Прожиг заканчивается, когда статус 0 держится " +\n'
    '                    "10 минут.\\n\\n" +\n'
    '                    "Прожиг помечается «завершён» (счётчик вырос или двигатель выключили в пределах 10 минут после " +\n'
    '                    "окончания) или «прерван» (двигатель выключили во время прожига или счётчик не вырос за 3 минуты). " +\n'
    '                    "В среднем участвуют все завершённые прожиги.\\n\\n" +\n')

KT.write_text(s, encoding="utf-8")
print("Готово: изменён", KT)
'''),
    ('patch_theme_first.py', r'''#!/usr/bin/env python3
"""Тема «Mazda gen4» первой в диалоге выбора темы.

Диалог сортирует элементы по алфавиту (ImageListPreference.kt). Название, начинающееся с «Mazda»,
получает пустой ключ сортировки и встаёт первым, остальные остаются по алфавиту. Сохранённый
выбор темы не затрагивается (он хранится по названию).

Запуск из корня репозитория:  python3 patch_theme_first.py
Повторный запуск безопасен.
"""
import sys
from pathlib import Path

# путь к файлу не гадаем: ищем его по имени (и проверяем, что это тот самый класс)
cands = [p for p in Path("app/src").rglob("ImageListPreference.kt") if "class ImageListPreference" in p.read_text(encoding="utf-8")]
if len(cands) != 1:
    sys.exit("Не найден ровно один ImageListPreference.kt в app/src (найдено %d). Запускайте скрипт из корня репозитория." % len(cands))
KT = cands[0]

s = KT.read_text(encoding="utf-8")
if 'startsWith("Mazda")' in s:
    sys.exit("Патч уже применён, ничего не меняю.")

old = "        }.sortedBy { it.title }\n"
new = '        }.sortedBy { if (it.title.startsWith("Mazda")) "" else it.title }   // «Mazda gen4» первой, остальные по алфавиту\n'
if s.count(old) != 1:
    sys.exit("Не найден ровно один фрагмент (найдено %d): %r" % (s.count(old), old))
KT.write_text(s.replace(old, new), encoding="utf-8")
print("Готово: изменён", KT)
'''),
]

results = []
for name, src in SCRIPTS:
    print("=== " + name)
    try:
        exec(compile(src, name, "exec"), {"__name__": "__main__"})
        results.append((name, "применён"))
    except SystemExit as e:
        msg = e.code if isinstance(e.code, str) else ""
        if msg:
            print(msg)
        results.append((name, "уже применён" if msg.startswith("Патч уже применён") else ("ОШИБКА: " + msg if msg else "применён")))

print("\n=== Итог")
for name, status in results:
    print("%-28s %s" % (name, status))
if any(st.startswith("ОШИБКА") for _, st in results):
    sys.exit(1)
