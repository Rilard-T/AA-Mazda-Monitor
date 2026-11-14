#!/usr/bin/env python3
"""0.8.3, правка карточек 2: убрать оставшиеся полоски (линии-разделители библиотеки настроек, в том числе
после последней строки списка). Подмена рисунка разделителя не сработала, поэтому список просто не принимает
собственную декорацию-разделитель библиотеки (карточкам она не нужна, зазоры рисуем сами).
Запускать из корня репозитория после patch_083_cards_fix.py:  python3 patch_083_cards_fix2.py"""
import pathlib, sys

kt = pathlib.Path("app/src/main/java/com/aatorque/prefs/CardRecyclerView.kt")
if not kt.exists():
    sys.exit("СТОП: нет CardRecyclerView.kt (не применялся patch_083_cards.py?).")
t = kt.read_text(encoding="utf-8")
if "DividerDecoration" in t:
    sys.exit("СТОП: исправление уже применено. Файлы не тронуты.")
ANCHOR = "    init {\n        addItemDecoration(CardDecoration(context))\n    }\n"
if t.count(ANCHOR) != 1:
    sys.exit("СТОП: CardRecyclerView.kt не в ожидаемом виде. Файлы не тронуты.")
ADD = '''
    /** Библиотека настроек добавляет свои линии-разделители; карточкам они не нужны, поэтому не принимаем их. */
    override fun addItemDecoration(decor: RecyclerView.ItemDecoration, index: Int) {
        if (decor.javaClass.name.contains("DividerDecoration")) return
        super.addItemDecoration(decor, index)
    }
'''
kt.write_text(t.replace(ANCHOR, ANCHOR + ADD), encoding="utf-8")
print("изменён", kt); print("Готово.")
