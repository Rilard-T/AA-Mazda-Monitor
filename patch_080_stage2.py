#!/usr/bin/env python3
"""0.8.0, этап 2: диалоги в стиле Material 3.
Запускать из корня репозитория ПОСЛЕ patch_080_stage1.py:  python3 patch_080_stage2.py
Заменяет системный android.app.AlertDialog на androidx.appcompat.app.AlertDialog:
в теме Material 3 он рисуется как диалог Material You (скруглённые углы, цвета из обоев).
Вызовы AlertDialog.Builder(...) в коде не меняются."""
import sys, pathlib

R = pathlib.Path("app/src/main/java/com/aatorque")
files = [R/"obd/AppExit.kt", R/"obd/ErrorReport.kt", R/"prefs/ColorPreference.kt",
         R/"prefs/FormulaPreference.kt", R/"prefs/SettingsActivity.kt"]
OLD, NEW = "import android.app.AlertDialog", "import androidx.appcompat.app.AlertDialog"

man = pathlib.Path("app/src/main/AndroidManifest.xml")
if not man.exists() or "Theme.App.Settings" not in man.read_text(encoding="utf-8"):
    sys.exit("СТОП: не найден этап 1 (Theme.App.Settings в манифесте). Сначала запусти patch_080_stage1.py.")

texts = {}
for f in files:
    if not f.exists():
        sys.exit(f"СТОП: нет файла {f}. Запусти скрипт из корня репозитория, в ветке с этапом 1.")
    t = f.read_text(encoding="utf-8")
    if t.count(OLD) != 1:
        sys.exit(f"СТОП: в {f} строка «{OLD}» найдена {t.count(OLD)} раз (ожидалась 1). Файлы не тронуты.")
    if NEW in t:
        sys.exit(f"СТОП: в {f} уже есть «{NEW}».")
    texts[f] = t
for f, t in texts.items():          # пишем только если проверены все файлы
    f.write_text(t.replace(OLD, NEW), encoding="utf-8")
    print("изменён", f)
print("Готово: 5 файлов.")
