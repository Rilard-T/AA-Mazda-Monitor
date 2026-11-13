#!/usr/bin/env python3
"""0.8.1, правка 3: вернуть переключатели в настройках.
В раскладках из patch_080_fix2.py контейнер виджета назывался @+id/widget_frame, а библиотека androidx.preference
ищет системный @android:id/widget_frame, поэтому переключатель не создавался. Правит уже применённый fix2.
(Если fix2 ещё не применён, скачай его заново: там это уже исправлено, а этот патч не нужен.)
Запускать из корня репозитория:  python3 patch_080_fix3.py"""
import pathlib, sys

L = pathlib.Path("app/src/main/res/layout")
OLD, NEW = 'android:id="@+id/widget_frame"', 'android:id="@android:id/widget_frame"'
texts = {}
for n in ("preference.xml", "preference_material.xml"):
    f = L / n
    if not f.exists():
        sys.exit(f"СТОП: нет {f} (patch_080_fix2.py не применялся?).")
    t = f.read_text(encoding="utf-8")
    if NEW in t:
        sys.exit(f"СТОП: в {f} уже стоит {NEW} (патч применён). Файлы не тронуты.")
    if t.count(OLD) != 1:
        sys.exit(f"СТОП: в {f} не найден {OLD} в единственном числе. Файлы не тронуты.")
    texts[f] = t
for f, t in texts.items():
    f.write_text(t.replace(OLD, NEW), encoding="utf-8"); print("изменён", f)
print("Готово.")
