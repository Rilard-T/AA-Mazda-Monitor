#!/usr/bin/env python3
"""0.8.0, этап 1: тема Material You для экранов настроек.
Запускать из корня репозитория:  python3 patch_080_stage1.py
Тема приборной панели (AppTheme и AppTheme.*) НЕ меняется."""
import re, sys, pathlib

R = pathlib.Path("app/src/main")
changed = []

def read(p):
    return pathlib.Path(p).read_text(encoding="utf-8")

def write(p, text):
    p = pathlib.Path(p); p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8"); changed.append(str(p))

def sub_once(path, pattern, repl, flags=0):
    s = read(path)
    new, n = re.subn(pattern, repl, s, count=0, flags=flags)
    if n != 1:
        sys.exit(f"СТОП: в {path} найдено совпадений: {n} (ожидалось 1) для {pattern!r}. Ничего не сломано, файл не тронут.")
    write(path, new)

for must in ["AndroidManifest.xml", "java/com/aatorque/prefs/SettingsActivity.kt",
             "java/com/aatorque/prefs/DashboardPreviewFragment.kt",
             "java/com/aatorque/obd/ObdSettingsActivity.kt", "java/com/aatorque/obd/ObdExperimental.kt"]:
    if not (R / must).exists():
        sys.exit(f"СТОП: нет файла {R/must}. Запусти скрипт из корня репозитория и в нужной ветке.")
if "patchVersion=5" not in read("version.properties"):
    sys.exit("СТОП: в version.properties не patchVersion=5. Ветка не содержит 0.7.5, сначала подтяни изменения из master.")

# ---------- 1. цвета (запасная палитра для Android 9-11; на Android 12+ их заменят динамические) ----------
def colors(d):
    return '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "".join(
        f'    <color name="ms_{k}">{v}</color>\n' for k, v in d.items()) + '</resources>\n'

light = dict(primary="#0058CB", on_primary="#FFFFFF", primary_container="#D8E2FF", on_primary_container="#001A41",
    secondary="#575E71", on_secondary="#FFFFFF", secondary_container="#DBE2F9", on_secondary_container="#141B2C",
    tertiary="#715573", on_tertiary="#FFFFFF", tertiary_container="#FBD7FC", on_tertiary_container="#29132D",
    error="#BA1A1A", on_error="#FFFFFF", error_container="#FFDAD6", on_error_container="#410002",
    background="#FAF9FD", on_background="#1A1B20", surface="#FAF9FD", on_surface="#1A1B20",
    surface_variant="#E1E2EC", on_surface_variant="#44474F", outline="#74777F", outline_variant="#C4C6D0",
    sc_lowest="#FFFFFF", sc_low="#F4F3F7", sc="#EEEDF1", sc_high="#E8E7EB", sc_highest="#E3E2E6")
dark = dict(primary="#ADC6FF", on_primary="#002E69", primary_container="#004494", on_primary_container="#D8E2FF",
    secondary="#BFC6DC", on_secondary="#293041", secondary_container="#3F4759", on_secondary_container="#DBE2F9",
    tertiary="#DEBCDF", on_tertiary="#402843", tertiary_container="#583E5B", on_tertiary_container="#FBD7FC",
    error="#FFB4AB", on_error="#690005", error_container="#93000A", on_error_container="#FFDAD6",
    background="#121316", on_background="#E3E2E6", surface="#121316", on_surface="#E3E2E6",
    surface_variant="#44474F", on_surface_variant="#C4C6D0", outline="#8E9099", outline_variant="#44474F",
    sc_lowest="#0D0E11", sc_low="#1A1B20", sc="#1E1F23", sc_high="#292A2D", sc_highest="#343538")
write(R / "res/values/colors_ms.xml", colors(light))
write(R / "res/values-night/colors_ms.xml", colors(dark))

# ---------- 2. тема только для экранов настроек ----------
roles = [("colorPrimary","primary"),("colorOnPrimary","on_primary"),("colorPrimaryContainer","primary_container"),
 ("colorOnPrimaryContainer","on_primary_container"),("colorSecondary","secondary"),("colorOnSecondary","on_secondary"),
 ("colorSecondaryContainer","secondary_container"),("colorOnSecondaryContainer","on_secondary_container"),
 ("colorTertiary","tertiary"),("colorOnTertiary","on_tertiary"),("colorTertiaryContainer","tertiary_container"),
 ("colorOnTertiaryContainer","on_tertiary_container"),("colorError","error"),("colorOnError","on_error"),
 ("colorErrorContainer","error_container"),("colorOnErrorContainer","on_error_container"),
 ("android:colorBackground","background"),("colorOnBackground","on_background"),("colorSurface","surface"),
 ("colorOnSurface","on_surface"),("colorSurfaceVariant","surface_variant"),("colorOnSurfaceVariant","on_surface_variant"),
 ("colorOutline","outline"),("colorOutlineVariant","outline_variant"),
 ("colorSurfaceContainerLowest","sc_lowest"),("colorSurfaceContainerLow","sc_low"),("colorSurfaceContainer","sc"),
 ("colorSurfaceContainerHigh","sc_high"),("colorSurfaceContainerHighest","sc_highest")]
theme = ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
 '    <!-- Тема экранов настроек (0.8.0). Приборная панель использует AppTheme и AppTheme.*, их не трогаем. -->\n'
 '    <style name="Theme.App.Settings" parent="Theme.Material3.DayNight">\n' +
 "".join(f'        <item name="{a}">@color/ms_{c}</item>\n' for a, c in roles) +
 '    </style>\n</resources>\n')
write(R / "res/values/themes_settings.xml", theme)

# ---------- 3. манифест: тема трём Activity настроек ----------
man = R / "AndroidManifest.xml"
for name in ["com.aatorque.prefs.SettingsActivity", "com.aatorque.obd.ObdSettingsActivity", "com.aatorque.obd.ObdExperimentalActivity"]:
    sub_once(man, r'(\n([ \t]*)android:name="%s")' % re.escape(name),
             lambda m: m.group(1) + '\n' + m.group(2) + 'android:theme="@style/Theme.App.Settings"')

# ---------- 4. динамические цвета в Activity ----------
DYN = "com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)"
sub_once(R / "java/com/aatorque/prefs/SettingsActivity.kt",
         r'(public override fun onCreate\(savedInstanceState: Bundle\?\) \{\n)(        super\.onCreate\(savedInstanceState\)\n        startObd\(\))',
         lambda m: m.group(1) + "        " + DYN + "\n" + m.group(2))
sub_once(R / "java/com/aatorque/obd/ObdSettingsActivity.kt", r'setTheme\(R\.style\.AppTheme\)[^\n]*',
         lambda m: "setTheme(R.style.Theme_App_Settings)\n        " + DYN)
sub_once(R / "java/com/aatorque/obd/ObdExperimental.kt", r'setTheme\(R\.style\.AppTheme\)[^\n]*',
         lambda m: "setTheme(R.style.Theme_App_Settings)\n        " + DYN)

# ---------- 5. предпросмотр приборки подменяет тему Activity: возвращаем тему настроек при выходе ----------
sub_once(R / "java/com/aatorque/prefs/DashboardPreviewFragment.kt",
         r'(    override fun onResume\(\) \{\n        super\.onResume\(\)\n        forceRotate\(true\))',
         lambda m: ("    override fun onDestroyView() {\n        super.onDestroyView()\n"
                    "        // предпросмотр ставит Activity тему прибора; после выхода возвращаем тему настроек\n"
                    "        activity?.let {\n            it.setTheme(R.style.Theme_App_Settings)\n"
                    "            com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(it)\n        }\n    }\n\n") + m.group(1))

# ---------- 6. версия 0.8.0 ----------
v = read("version.properties")
if "minorVersion=7" not in v:
    sys.exit("СТОП: в version.properties нет minorVersion=7")
write("version.properties", v.replace("minorVersion=7", "minorVersion=8").replace("patchVersion=5", "patchVersion=0"))

print("Готово. Изменено/создано:"); [print("  ", c) for c in changed]
