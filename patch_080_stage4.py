#!/usr/bin/env python3
"""0.8.0, этап 4: экран оповещений (Compose) в стиле Material You.
Запускать из корня репозитория:  python3 patch_080_stage4.py
 - AppTheme (Compose): на Android 12+ цвета берутся из обоев (dynamic color), ниже 12 остаётся прежняя палитра;
 - кнопки диалога «Confirm» / «Dismiss» -> системные «ОК» / «Отмена» (переводятся автоматически)."""
import pathlib, sys

SRC = pathlib.Path("app/src/main/java")
if not SRC.exists():
    sys.exit("СТОП: запусти скрипт из корня репозитория.")

# --- 1. тема Compose ---
cands = [p for p in SRC.rglob("*.kt")
         if "fun AppTheme(" in (t := p.read_text(encoding="utf-8")) and "package com.aatorque.ui" in t]
if len(cands) != 1:
    sys.exit(f"СТОП: файлов с AppTheme найдено {len(cands)} (ожидался 1): {cands}")
theme = cands[0]
t = theme.read_text(encoding="utf-8")
IMP = "import androidx.compose.runtime.Composable\n"
OLD = ("  val colors = if (!useDarkTheme) {\n    LightColors\n  } else {\n    DarkColors\n  }\n")
if t.count(IMP) != 1 or t.count(OLD) != 1 or "dynamicLightColorScheme" in t:
    sys.exit(f"СТОП: {theme} не похож на ожидаемый (или этап 4 уже применён). Файлы не тронуты.")
NEW = ("  val context = LocalContext.current\n"
       "  val colors = when {\n"
       "    // Android 12+: цвета из обоев (Material You)\n"
       "    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->\n"
       "      if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)\n"
       "    useDarkTheme -> DarkColors\n"
       "    else -> LightColors\n"
       "  }\n")
t = t.replace(OLD, NEW).replace(IMP, IMP +
    "import android.os.Build\n"
    "import androidx.compose.material3.dynamicDarkColorScheme\n"
    "import androidx.compose.material3.dynamicLightColorScheme\n"
    "import androidx.compose.ui.platform.LocalContext\n")

# --- 2. кнопки диалога ---
alarm = SRC / "com/aatorque/prefs/AlarmFragment.kt"
if not alarm.exists():
    sys.exit(f"СТОП: нет {alarm}")
a = alarm.read_text(encoding="utf-8")
C, D = 'Text("Confirm")', 'Text("Dismiss")'
if a.count(C) != 1 or a.count(D) != 1:
    sys.exit("СТОП: в AlarmFragment.kt не найдены кнопки Confirm/Dismiss в ожидаемом виде. Файлы не тронуты.")
a = a.replace(C, "Text(stringResource(android.R.string.ok))").replace(D, "Text(stringResource(android.R.string.cancel))")

theme.write_text(t, encoding="utf-8"); alarm.write_text(a, encoding="utf-8")
print("изменён", theme); print("изменён", alarm); print("Готово.")
