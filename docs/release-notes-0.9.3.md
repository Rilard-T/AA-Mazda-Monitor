AA Mazda Monitor 0.9.3

**Русский**

- Добавлен отключаемый индикатор связи OBD (компактная точка статуса в правом верхнем углу дашборда и переключатель «Индикатор связи OBD» в настройках).
- В раздел настроек «Экспериментальные функции» добавлена «Статистика прожигов».
- Неотвечающие датчики OBD автоматически уходят в карантин на поездку после 3 пустых ответов подряд; на экране «Проверка датчиков» добавлены ручное управление и отключение неотвечающих.
- Снижена нагрузка опроса OBD за счёт кэширования настроек и объединения повторных запросов EGR; исправлены потоки при проверке обновлений OTA.
- Исправлен ряд критических багов и утечек (перепутанные min/max в настройках датчиков, утечки `BroadcastReceiver`, `OnActiveSessionsChangedListener`, `TypedArray`, `observeForever`, гонки потоков и деление на ноль на графике).
- Версия 0.9.3 — ставится поверх предыдущих с тем же ключом подписи без удаления.

**English**

- Added a toggleable OBD link indicator (compact status dot in the top-right corner of the dashboard and "OBD link indicator" toggle in Settings).
- Added "Regeneration stats" under Experimental features in Settings.
- Unresponsive OBD sensors are automatically quarantined for the trip after 3 consecutive empty replies, with manual toggle and "Disable unresponsive" in Sensor Check.
- Reduced OBD polling overhead by caching preferences and sharing duplicate EGR reads; fixed UI threading in OTA update checks.
- Fixed multiple critical bugs and leaks (swapped min/max in sensor settings, `BroadcastReceiver`, `OnActiveSessionsChangedListener`, `TypedArray`, and `observeForever` leaks, thread races, and chart division by zero).
- Version 0.9.3 — installs over previous releases with the same signing key without uninstalling.
