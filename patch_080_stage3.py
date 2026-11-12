#!/usr/bin/env python3
"""0.8.0, этап 3: элементы настроек в стиле Material 3.
Запускать из корня репозитория после patch_080_stage1.py:  python3 patch_080_stage3.py
 - флажки (CheckBoxPreference) -> переключатели (SwitchPreferenceCompat): 10 штук;
 - диалог формул (script_preference.xml): подписи, отступы, тональная кнопка;
 - список выбора темы/значка (icon_list_row.xml): скруглённые значки, шрифт Material 3."""
import re, sys, pathlib

M = pathlib.Path("app/src/main")
KT = [M/"java/com/aatorque/prefs/SettingsFragment.kt", M/"java/com/aatorque/prefs/SettingsPIDFragment.kt"]
XML = [M/"res/xml/settings.xml", M/"res/xml/pid_setting.xml"]
SCRIPT, ROW = M/"res/layout/script_preference.xml", M/"res/layout/icon_list_row.xml"

man = M/"AndroidManifest.xml"
if not man.exists() or "Theme.App.Settings" not in man.read_text(encoding="utf-8"):
    sys.exit("СТОП: не найден этап 1 (Theme.App.Settings в манифесте).")
for f in KT + XML + [SCRIPT, ROW]:
    if not f.exists():
        sys.exit(f"СТОП: нет файла {f}. Запусти из корня репозитория, в ветке с этапом 1.")

out = {}
# --- Kotlin ---
IMP_C, IMP_S = "import androidx.preference.CheckBoxPreference\n", "import androidx.preference.SwitchPreferenceCompat\n"
for f in KT:
    t = f.read_text(encoding="utf-8")
    if t.count(IMP_C) != 1:
        sys.exit(f"СТОП: в {f} нет ровно одного импорта CheckBoxPreference (или этап 3 уже применён).")
    t = t.replace(IMP_C, "" if IMP_S in t else IMP_S)           # без дубля импорта
    t = re.sub(r"\bCheckBoxPreference\b", "SwitchPreferenceCompat", t)
    out[f] = t
# --- XML настроек ---
total = 0
for f in XML:
    t = f.read_text(encoding="utf-8")
    t, n = re.subn(r"\bCheckBoxPreference\b", "SwitchPreferenceCompat", t)
    total += n; out[f] = t
if total != 10:
    sys.exit(f"СТОП: в xml найдено {total} флажков вместо 10. Ничего не изменено.")
# --- диалог формул ---
s = SCRIPT.read_text(encoding="utf-8")
if "@+id/openDocumentation" not in s:
    sys.exit("СТОП: script_preference.xml не похож на ожидаемый.")
LBL = ('    android:textAppearance="?attr/textAppearanceLabelLarge"\n'
       '    android:textColor="?attr/colorPrimary"')
out[SCRIPT] = f'''<?xml version="1.0" encoding="utf-8"?>
<androidx.appcompat.widget.LinearLayoutCompat
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center"
    android:orientation="vertical"
    android:paddingHorizontal="24dp"
    android:paddingVertical="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/formulas"
{LBL}/>

    <Spinner
        android:id="@+id/spinner"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="16dp"
        android:dropDownWidth="match_parent"
        android:spinnerMode="dropdown" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/custom_script"
{LBL}/>

    <EditText
        android:id="@+id/text_field"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="16dp"/>

    <Button
        android:id="@+id/openDocumentation"
        style="@style/Widget.Material3.Button.TonalButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/expressionDocumentation" />

</androidx.appcompat.widget.LinearLayoutCompat>
'''
# --- строка списка со значком ---
r = ROW.read_text(encoding="utf-8")
A, B = "androidx.appcompat.widget.AppCompatImageView", "?android:attr/textAppearanceListItem"
if r.count(A) != 1 or r.count(B) != 1:
    sys.exit("СТОП: icon_list_row.xml не похож на ожидаемый.")
r = r.replace(A, "com.google.android.material.imageview.ShapeableImageView")
r = r.replace('android:scaleType="centerCrop" />', 'android:scaleType="centerCrop"\n        app:shapeAppearanceOverlay="@style/ShapeAppearanceOverlay.Material3.Corner.Medium" />', 1)
r = r.replace(B, "?attr/textAppearanceBodyLarge")
if "shapeAppearanceOverlay" not in r:
    sys.exit("СТОП: не удалось добавить скругление в icon_list_row.xml.")
out[ROW] = r

for f, t in out.items():                 # пишем только после успешных проверок
    f.write_text(t, encoding="utf-8"); print("изменён", f)
print("Готово.")
