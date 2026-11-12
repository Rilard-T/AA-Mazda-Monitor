#!/usr/bin/env python3
"""Диагностика температуры перед сажевым (01 7C) и перепада давления (01 7A).

Что меняет (только логирование, расчёты и версия не затрагиваются):
  1. При отсутствии значения у 7C и 7A пишет в журнал сырой ответ блока.
  2. Увеличивает длину записываемого ответа с 60 до 120 символов.
  3. При подключении пишет в журнал список параметров режима 01, которые заявил блок.

Запуск из корня репозитория:  python3 patch_dpf_diag.py
Повторный запуск безопасен (уже применённый патч пропускается).
"""
import sys
from pathlib import Path

PATH = Path("app/src/main/java/com/aatorque/obd/ObdEngine.kt")
MARK = "диагностика 7C/7A"

if not PATH.exists():
    sys.exit("Не найден " + str(PATH) + ". Запускайте скрипт из корня репозитория.")

s = PATH.read_text(encoding="utf-8")
if MARK in s:
    sys.exit("Патч уже применён, ничего не меняю.")

def rep(old, new):
    global s
    n = s.count(old)
    if n != 1:
        sys.exit("Не найден ровно один фрагмент (найдено %d): %r" % (n, old[:80]))
    s = s.replace(old, new)

# 1. сырой ответ при отсутствии значения у 7C и 7A
rep(
    "            if (g != null && t - g.t < HOLD_SLOW_MS) shown = g.v\n        }\n",
    "            if (g != null && t - g.t < HOLD_SLOW_MS) shown = g.v\n"
    "        } else if (p.virtual == null && (p.id == \"7c\" || p.id == \"7a\")) {\n"
    "            warnMiss(p, t)   // " + MARK + ": сырой ответ блока\n"
    "        }\n",
)

# 2. ответ в журнале не обрезаем так рано
rep(
    'lastRaw.replace(Regex("\\\\s+"), " ").take(60))',
    'lastRaw.replace(Regex("\\\\s+"), " ").take(120))',
)

# 3. список параметров режима 01, заявленных блоком
rep(
    "        readSupported(r)\n",
    "        readSupported(r)\n"
    "        Timber.i(\"Режим 01, заявлено блоком: %s\", supported.sorted().joinToString(\",\") { \"%02X\".format(it) })\n",
)

PATH.write_text(s, encoding="utf-8")
print("Готово: изменён", PATH)
