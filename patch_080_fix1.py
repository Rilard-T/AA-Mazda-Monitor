#!/usr/bin/env python3
"""0.8.1, правка 1 по итогам 0.8.0:
 - иконка предпросмотра (и ещё четыре иконки меню) была жёстко белой: теперь красится цветом темы, как шестерёнка;
 - строка состояния брала синий цвет по умолчанию: теперь тот же цвет, что у верхней панели, а значки
   светлые или тёмные по режиму (день/ночь).
Запускать из корня репозитория в master:  python3 patch_080_fix1.py"""
import pathlib, re, sys

RES = pathlib.Path("app/src/main/res")
theme = RES / "values/themes_settings.xml"
if not theme.exists() or "Theme.App.Settings" not in theme.read_text(encoding="utf-8"):
    sys.exit("СТОП: не найден themes_settings.xml с Theme.App.Settings (этап 1 из 0.8.0).")
tt = theme.read_text(encoding="utf-8")
if "statusBarColor" in tt:
    sys.exit("СТОП: в themes_settings.xml уже есть statusBarColor (патч уже применён?). Файлы не тронуты.")
TAIL = "    </style>\n</resources>"
if tt.count(TAIL) != 1:
    sys.exit("СТОП: конец themes_settings.xml не в ожидаемом виде. Файлы не тронуты.")

# иконки
icons = {}
for n in ["baseline_preview_24", "baseline_content_copy_24", "baseline_export", "baseline_file_download_24", "baseline_info_24"]:
    f = RES / f"drawable/{n}.xml"
    if not f.exists():
        sys.exit(f"СТОП: нет {f}")
    t = f.read_text(encoding="utf-8")
    new, k = re.subn(r'android:tint="(?:#FFF|#FFFFFF)"', 'android:tint="?attr/colorControlNormal"', t)
    if k != 1:
        sys.exit(f"СТОП: в {f} не найден жёсткий tint (ожидался один). Файлы не тронуты.")
    icons[f] = new

NEW_TAIL = ('        <!-- верхняя панель и строка состояния одного цвета; значки строки состояния по режиму -->\n'
            '        <item name="actionBarStyle">@style/Widget.App.ActionBar</item>\n'
            '        <item name="android:statusBarColor">?attr/colorSurfaceContainer</item>\n'
            '        <item name="android:windowLightStatusBar">@bool/ms_light_bars</item>\n'
            '    </style>\n\n'
            '    <style name="Widget.App.ActionBar" parent="Widget.Material3.ActionBar.Solid">\n'
            '        <item name="background">?attr/colorSurfaceContainer</item>\n'
            '    </style>\n</resources>')
def bools(v):
    return f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <bool name="ms_light_bars">{v}</bool>\n</resources>\n'

# --- запись только после всех проверок ---
for f, t in icons.items():
    f.write_text(t, encoding="utf-8"); print("изменён", f)
theme.write_text(tt.replace(TAIL, NEW_TAIL), encoding="utf-8"); print("изменён", theme)
for d, v in (("values", "true"), ("values-night", "false")):
    p = RES / d / "bools_ms.xml"; p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(bools(v), encoding="utf-8"); print("создан", p)

vp = pathlib.Path("version.properties")
s = vp.read_text(encoding="utf-8")
if "minorVersion=8" in s and "patchVersion=0" in s:
    vp.write_text(s.replace("patchVersion=0", "patchVersion=1"), encoding="utf-8"); print("версия: 0.8.1")
else:
    print("версию не менял (в version.properties не 0.8.0); при необходимости подними patchVersion сам")
