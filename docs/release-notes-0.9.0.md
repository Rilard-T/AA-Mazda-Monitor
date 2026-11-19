AA Mazda Monitor 0.9.0

**Русский**

- Приложение разделено на два независимых канала: AA Mazda Monitor (мониторинг) и AA Dash Nav (навигация в Android Auto). У каждого свой пакет, оба ставятся одновременно и не мешают друг другу.
- Общее ядро мониторинга вынесено в общий код, брендинг (название, иконка, строки) — в отдельный слой по каналу.
- Исправлено обновление по OTA: кнопка «Скачать» больше не ведёт на несуществующий файл — имя APK берётся из фактического релиза, старые и новые выпуски скачиваются одинаково.
- Сохранены улучшения OBD из 0.8.9: пользовательские PID с формулами, проверка датчиков с отметкой «Нет ответа», бенчмарк адаптера.
- Версия 0.9.0 — ставится поверх 0.8.9 без удаления.

**English**

- The app is split into two independent channels: AA Mazda Monitor (monitoring) and AA Dash Nav (Android Auto navigation). Each has its own package, both install side by side.
- Shared monitoring core with per-channel branding (name, icon, strings) kept in a separate layer.
- OTA update fixed: the “Download” button no longer points to a missing file — the APK name is taken from the actual release, so old and new releases download the same way.
- OBD improvements from 0.8.9 are kept: custom PIDs with formulas, sensor check with “No answer” marks, adapter benchmark.
- Version 0.9.0 — installs over 0.8.9 without uninstalling.
