AA Mazda Monitor 0.9.1

**Русский**

- Установка обновлений по воздуху теперь идёт с Play-источником: APK ставится с метаданными Android Auto без AAEnabler (EXTRA_INSTALLER_PACKAGE_NAME=com.android.vending).
- После скачивания обновления показывается диалог «Загрузка завершена» с кнопкой «Установить из Play-источника»; при несовпадении SHA-256 файл удаляется и показывается сообщение о повреждении.
- В настройках добавлен пункт «Установить APK»: выбор между установкой из Play-источника и выбором файла из памяти (APK).
- При первом запуске показывается пояснение об установке с Play-источником.
- Бэкап профилей обновлён до v2: формат AA_MONITOR_BACKUP;2 включает раскладку приборов (layout), сохранена совместимость с v1 и с файлами NAV.
- Экспорт/импорт бэкапа учитывают поле layout; неизвестный JSON игнорируется.
- Версия 0.9.1 — ставится поверх 0.9.0 без удаления.

**English**

- OTA updates now install with Play source: the APK is installed with Android Auto metadata without AAEnabler (EXTRA_INSTALLER_PACKAGE_NAME=com.android.vending).
- After an update is downloaded, a “Download complete” dialog offers “Install from Play source”; on SHA-256 mismatch the file is removed and a corruption message is shown.
- Settings gained an “Install APK” item: choose between Play-source install and picking a file from storage (APK).
- A first-run dialog explains Play-source installation.
- Profile backup upgraded to v2: AA_MONITOR_BACKUP;2 format includes the gauge layout, with backward compatibility for v1 and NAV files.
- Backup export/import handle the layout field; unknown JSON is ignored.
- Version 0.9.1 — installs over 0.9.0 without uninstalling.
