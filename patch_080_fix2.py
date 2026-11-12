#!/usr/bin/env python3
"""0.8.1, правка 2: текст настроек по левому краю, значки (если есть) справа от текста.
Приложение подменяет три раскладки библиотеки androidx.preference своими (одноимённые файлы в res/layout):
строка настройки, заголовок раздела и ползунок. Код и xml настроек не меняются.
Запускать из корня репозитория:  python3 patch_080_fix2.py"""
import pathlib, sys, xml.dom.minidom

L = pathlib.Path("app/src/main/res/layout")
if not (L / "activity_settings.xml").exists():
    sys.exit("СТОП: запусти скрипт из корня репозитория (нет res/layout/activity_settings.xml).")

NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<!-- 0.8.1: текст по левому краю, значок (если есть) справа от текста, перед виджетом -->\n'

PREF = HEAD + f'''<LinearLayout {NS}
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="?android:attr/listPreferredItemHeightSmall"
    android:gravity="center_vertical"
    android:paddingStart="?android:attr/listPreferredItemPaddingStart"
    android:paddingEnd="?android:attr/listPreferredItemPaddingEnd"
    android:background="?android:attr/selectableItemBackground"
    android:clipToPadding="false">

    <RelativeLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:paddingTop="16dp"
        android:paddingBottom="16dp">

        <TextView
            android:id="@android:id/title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:ellipsize="marquee"
            android:fadingEdge="horizontal"
            android:singleLine="true"
            android:textAlignment="viewStart"
            android:textAppearance="?android:attr/textAppearanceListItem" />

        <TextView
            android:id="@android:id/summary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_alignStart="@android:id/title"
            android:layout_below="@android:id/title"
            android:maxLines="10"
            android:textAlignment="viewStart"
            android:textAppearance="?android:attr/textAppearanceListItemSecondary"
            android:textColor="?android:attr/textColorSecondary" />
    </RelativeLayout>

    <LinearLayout
        android:id="@+id/icon_frame"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:gravity="end|center_vertical"
        android:orientation="horizontal"
        android:paddingStart="12dp"
        android:paddingTop="4dp"
        android:paddingBottom="4dp">

        <ImageView
            android:id="@android:id/icon"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:adjustViewBounds="true"
            android:maxWidth="48dp"
            android:maxHeight="48dp"
            android:scaleType="fitCenter" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/widget_frame"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:gravity="end|center_vertical"
        android:orientation="vertical"
        android:paddingStart="16dp" />
</LinearLayout>
'''

CATEGORY = HEAD + f'''<LinearLayout {NS}
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingStart="?android:attr/listPreferredItemPaddingStart"
    android:paddingEnd="?android:attr/listPreferredItemPaddingEnd"
    android:paddingTop="16dp"
    android:paddingBottom="8dp">

    <TextView
        android:id="@android:id/title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAlignment="viewStart"
        android:textAppearance="@style/TextAppearance.AppCompat.Body2"
        android:textColor="?attr/colorAccent" />

    <TextView
        android:id="@android:id/summary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAlignment="viewStart"
        android:textAppearance="?android:attr/textAppearanceListItemSecondary"
        android:textColor="?android:attr/textColorSecondary" />
</LinearLayout>
'''

SEEK = HEAD + f'''<LinearLayout {NS}
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="?android:attr/listPreferredItemHeightSmall"
    android:gravity="center_vertical"
    android:paddingStart="?android:attr/listPreferredItemPaddingStart"
    android:paddingEnd="?android:attr/listPreferredItemPaddingEnd"
    android:background="?android:attr/selectableItemBackground"
    android:clipToPadding="false">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:paddingTop="16dp"
        android:paddingBottom="16dp">

        <TextView
            android:id="@android:id/title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:ellipsize="marquee"
            android:fadingEdge="horizontal"
            android:singleLine="true"
            android:textAlignment="viewStart"
            android:textAppearance="?android:attr/textAppearanceListItem" />

        <TextView
            android:id="@android:id/summary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:maxLines="4"
            android:textAlignment="viewStart"
            android:textAppearance="?android:attr/textAppearanceListItemSecondary"
            android:textColor="?android:attr/textColorSecondary" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <SeekBar
                android:id="@+id/seekbar"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="-16dp"
                android:layout_weight="1" />

            <TextView
                android:id="@+id/seekbar_value"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:minWidth="48dp"
                android:gravity="end"
                android:textAppearance="?android:attr/textAppearanceListItem" />
        </LinearLayout>
    </LinearLayout>

    <LinearLayout
        android:id="@+id/icon_frame"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:gravity="end|center_vertical"
        android:orientation="horizontal"
        android:paddingStart="12dp">

        <ImageView
            android:id="@android:id/icon"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:adjustViewBounds="true"
            android:maxWidth="48dp"
            android:maxHeight="48dp"
            android:scaleType="fitCenter" />
    </LinearLayout>
</LinearLayout>
'''

# одноимённые файлы библиотеки: на случай, если используется как обычный, так и Material-вариант
files = {"preference.xml": PREF, "preference_material.xml": PREF,
         "preference_category.xml": CATEGORY, "preference_category_material.xml": CATEGORY,
         "preference_widget_seekbar.xml": SEEK, "preference_widget_seekbar_material.xml": SEEK}
for n, t in files.items():
    if (L / n).exists():
        sys.exit(f"СТОП: {L/n} уже существует (патч применён?). Файлы не тронуты.")
    xml.dom.minidom.parseString(t.encode("utf-8"))          # проверка корректности
for n, t in files.items():
    (L / n).write_text(t, encoding="utf-8"); print("создан", L / n)
print("Готово.")
