## Что изменилось / What's changed

**Русский**

- Добавлены два встроенных профиля: «Mazda Diesel» и «Универсальный OBD-II» (стандартные OBD-II PID без DPF), с безопасной миграцией существующих профилей и бесшовным переключением на лету без перезапуска Android Auto.
- Добавлен индикатор статуса связи OBD (точка на экране AA) для быстрого контроля подключения.
- На информационных плитках появилась настройка красной зоны («Красная зона…») с подсветкой превышения порога.
- В редакторе раскладки добавлены готовые шаблоны («Дизель DPF», «Универсальный OBD-II», «Трасса», «Минимализм»).
- В настройках добавлен раздел «Статистика прожигов» (число записей, средний интервал, средняя сажа и оценка пробега до следующего прожига).
- Оптимизирована отрисовка графиков (переиспользование `Path` и срез истории `historySince`).
- Размер круга прибора в редакторе можно увеличивать до 200 %; у края экрана ползунок автоматически ограничивается, чтобы прибор не выходил за границы области.
- Сохранённые пропорции экрана автомобиля теперь хранятся раздельно для широкой и узкой форм; высота предпросмотра в портрете ограничена 42 % экрана, чтобы панель кнопок всегда оставалась на месте.
- Неотвечающие датчики OBD автоматически уходят в карантин на поездку после 3 пустых ответов подряд; на экране «Проверка датчиков» добавлены ручное отключение/включение датчиков и кнопка «Отключить неотвечающие».
- Исправлены потоки при проверке обновлений OTA, бэкап журнала прожигов теперь привязан к активному профилю, убрана заглушка добавления графика на экране AA, а интерфейс редактора полностью локализован.
- Снижена нагрузка в поездке за счёт кэширования шрифтов, профилей и настроек опроса OBD, а также объединения повторного запроса EGR.

**English**

- Added two built-in profiles: "Mazda Diesel" and "Universal OBD-II" (standard OBD-II PIDs without DPF), with safe migration of existing profiles and seamless hot-switching without restarting Android Auto.
- Added an OBD link status indicator dot on the Android Auto screen.
- Added red-zone threshold highlighting for info tiles ("Red zone…") in the layout editor.
- Added ready-to-use layout templates ("Diesel DPF", "Universal OBD-II", "Highway", "Minimal").
- Added a "Regeneration stats" summary in Settings (entries count, average interval, average soot, and estimated distance to next regen).
- Optimized chart rendering by reusing `Path` instances and slicing history via `historySince`.
- Gauge circle size in the layout editor can now be increased up to 200%, with automatic clamping near screen edges so gauges stay inside the display area.
- Saved car screen proportions are now stored separately for wide and narrow forms; portrait preview height is capped at 42% so the control panel remains visible.
- Unresponsive OBD sensors are automatically quarantined for the current trip after 3 consecutive empty replies; the Sensor Check screen now lets you toggle sensors individually or disable all unresponsive ones.
- Fixed UI threading during OTA update checks, bound backup regeneration logs to the active profile, removed the placeholder graph row on the AA screen, and localized all remaining editor strings.
- Reduced CPU overhead while driving by caching fonts, profiles, and OBD polling settings, and by sharing duplicate EGR responses.
