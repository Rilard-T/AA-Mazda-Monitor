#!/usr/bin/env python3
"""Журнал: строка состояния раз в 30 секунд и метки времени.
После фильтра шума в журнал перестали попадать значения датчиков («Got valid ...»). Патч возвращает главное:
 - ObdEngine: раз в 30 с строка «Состояние ЧЧ:ММ:СС: сажа .. г/л (макс ..), DPF ..% (порог ..), статус, счётчик,
   пробег после прожига, обороты, скорость, ОЖ, АКПП, масло». Значения берутся из кэша, новых запросов к блоку нет
   (скорость, ОЖ, АКПП и масло есть, пока открыт экран приборов, иначе «—»);
 - CacheLogTree: в фильтр шума добавлены строки метаданных медиа («Sending new metadata», «Got new metadata»);
   перед каждой строкой журнала (буфер обмена, обращение на GitHub) метка «ММ-ДД ЧЧ:ММ:СС»;
   метод stamp(time) открыт, чтобы экспорт в файл мог им пользоваться.
Версию не меняет. Запускать из корня репозитория:  python3 patch_log_state.py"""
import pathlib, sys

eng = pathlib.Path("app/src/main/java/com/aatorque/obd/ObdEngine.kt")
tree = pathlib.Path("app/src/main/java/com/aatorque/stats/CacheLogTree.kt")
for f in (eng, tree):
    if not f.exists():
        sys.exit(f"СТОП: нет {f}. Запусти из корня репозитория.")
te, tt = eng.read_text(encoding="utf-8"), tree.read_text(encoding="utf-8")
if "private fun stateLog" in te or "fun stamp(" in tt:
    sys.exit("СТОП: патч уже применён. Файлы не тронуты.")

# --- ObdEngine ---
CALL_OLD = "if (n % 6 == 0) diagLog()"
CALL_NEW = "if (n % 6 == 0) { stateLog(); diagLog() }"
ANCHOR = "    /** Раз в 30 с пишет в журнал ответы блока на параметры, которые могут пригодиться для сажевого."
if te.count(CALL_OLD) != 1 or te.count(ANCHOR) != 1:
    sys.exit("СТОП: ObdEngine.kt не в ожидаемом виде (нет вызова diagLog в цикле или его описания). Файлы не тронуты.")
STATE = '''    /** Раз в 30 с пишет в журнал строку состояния (значения берутся из кэша, запросов к блоку нет). */
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

'''
te = te.replace(CALL_OLD, CALL_NEW).replace(ANCHOR, STATE + ANCHOR)

# --- CacheLogTree ---
LINE_OLD = '"${it.tag ?: ""}: ${levelName(it.priority)} ${it.message} ${it.throwable?.stackTraceToString() ?: ""}"'
LINE_NEW = '"${stamp(it.time)} ${it.tag ?: ""}: ${levelName(it.priority)} ${it.message} ${it.throwable?.stackTraceToString() ?: ""}"'
LEVEL = "    fun levelName(priority: Int): String"
if tt.count(LINE_OLD) != 1 or tt.count(LEVEL) != 1:
    sys.exit("СТОП: CacheLogTree.kt не в ожидаемом виде. Файлы не тронуты.")
STAMP = '''    private val stampFormat = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)

    /** Метка времени строки журнала. */
    fun stamp(time: Long): String = synchronized(stampFormat) { stampFormat.format(java.util.Date(time)) }

'''
tt = tt.replace(LINE_OLD, LINE_NEW).replace(LEVEL, STAMP + LEVEL)

# дополнительный шум: метаданные медиа (по ~20 строк в минуту, 77% журнала 13.11.2026)
NOISE_OLD = 'private val NOISE = listOf("Got valid")'
NOISE_NEW = 'private val NOISE = listOf("Got valid", "Sending new metadata", "Got new metadata")'
if tt.count(NOISE_OLD) != 1:
    sys.exit("СТОП: список NOISE в CacheLogTree.kt не в ожидаемом виде. Файлы не тронуты.")
tt = tt.replace(NOISE_OLD, NOISE_NEW)

eng.write_text(te, encoding="utf-8"); print("изменён", eng)
tree.write_text(tt, encoding="utf-8"); print("изменён", tree)
print("Готово.")
