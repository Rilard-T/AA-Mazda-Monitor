#!/usr/bin/env python3
"""Две правки для 0.7.6.
1) Журнал 0.7.5 обрезал многокадровые ответы блока (01 78 / 7A / 8B): остаются только первые 6 байт.
   Причина: к запросу добавляется «1» (режим быстрого адаптера: ждать один кадр). Для диагностики
   отправляем запрос без этой цифры, чтобы пришёл весь ответ. На обычное чтение датчиков это не влияет.
2) Индикатор «Прожиг» показывал «00/1/2»: к значению 0 дописывалась единица измерения «0/1/2».
   Единицу убираем, прибор покажет просто 0, 1 или 2.
Запускать из корня репозитория:  python3 patch_076_diag.py"""
import pathlib, sys

eng = pathlib.Path("app/src/main/java/com/aatorque/obd/ObdEngine.kt")
pids = pathlib.Path("app/src/main/java/com/aatorque/obd/Pids.kt")
for f in (eng, pids):
    if not f.exists():
        sys.exit(f"СТОП: нет {f}. Запусти из корня репозитория.")
te, tp = eng.read_text(encoding="utf-8"), pids.read_text(encoding="utf-8")

OLD1 = 'send("7E0", if (useCount) req + "1" else req, 1500)\n    } catch (e: IOException) { failures++; "ошибка связи: "'
NEW1 = ('// без цифры-счётчика: иначе адаптер отдаёт только первый кадр многокадрового ответа\n'
        '        send("7E0", req, 2500)\n    } catch (e: IOException) { failures++; "ошибка связи: "')
OLD2 = 'PidDef("ff9006", "Статус прожига сажевого", "Прожиг", "0/1/2", 0.0, 2.0,'
NEW2 = 'PidDef("ff9006", "Статус прожига сажевого", "Прожиг", "", 0.0, 2.0,'
if te.count(OLD1) != 1 or tp.count(OLD2) != 1:
    sys.exit("СТОП: код не в ожидаемом виде (или патч уже применён). Файлы не тронуты.")

eng.write_text(te.replace(OLD1, NEW1), encoding="utf-8"); print("изменён", eng)
pids.write_text(tp.replace(OLD2, NEW2), encoding="utf-8"); print("изменён", pids)

v = pathlib.Path("version.properties")
s = v.read_text(encoding="utf-8")
if "minorVersion=7" in s and "patchVersion=5" in s:
    v.write_text(s.replace("patchVersion=5", "patchVersion=6"), encoding="utf-8")
    print("версия: 0.7.6")
else:
    print("версию не менял (в version.properties не 0.7.5); при необходимости подними patchVersion сам")
