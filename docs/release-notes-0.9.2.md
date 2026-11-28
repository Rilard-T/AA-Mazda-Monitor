AA Mazda Monitor 0.9.2

**Русский**

- Исправлена ручная установка из Play-источника: APK скачивается из последнего релиза даже если версия не новее текущей (переустановка с Play-источником).
- Проверка наличия APK в выпуске стала надёжнее (GET вместо HEAD, т.к. GitHub отдаёт 302 на CDN).
- Версия 0.9.2 — ставится поверх 0.9.1 без удаления.

**English**

- Fixed manual Play-source install: the APK is downloaded from the latest release even when its version is not newer than the installed one (reinstall with Play source).
- Release APK detection is more robust (GET instead of HEAD, since GitHub returns 302 to CDN).
- Version 0.9.2 — installs over 0.9.1 without uninstalling.
