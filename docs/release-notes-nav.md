## Что изменилось / What's changed

**Русский**

- Размер круга прибора в редакторе можно увеличивать до 200 %; у края экрана ползунок автоматически ограничивается, чтобы прибор не выходил за границы области.
- Сохранённые пропорции экрана автомобиля теперь хранятся раздельно для широкой и узкой форм; высота предпросмотра в портрете ограничена 42 % экрана, чтобы панель кнопок всегда оставалась на месте.
- Неотвечающие датчики OBD автоматически уходят в карантин на поездку после 3 пустых ответов подряд; на экране «Проверка датчиков» добавлены ручное отключение/включение датчиков и кнопка «Отключить неотвечающие».
- Исправлены потоки при проверке обновлений OTA, бэкап журнала прожигов теперь привязан к активному профилю, убрана заглушка добавления графика на экране AA, а интерфейс редактора полностью локализован.
- Снижена нагрузка в поездке за счёт кэширования шрифтов, профилей и настроек опроса OBD, а также объединения повторного запроса EGR.

**English**

- Gauge circle size in the layout editor can now be increased up to 200%, with automatic clamping near screen edges so gauges stay inside the display area.
- Saved car screen proportions are now stored separately for wide and narrow forms; portrait preview height is capped at 42% so the control panel remains visible.
- Unresponsive OBD sensors are automatically quarantined for the current trip after 3 consecutive empty replies; the Sensor Check screen now lets you toggle sensors individually or disable all unresponsive ones.
- Fixed UI threading during OTA update checks, bound backup regeneration logs to the active profile, removed the placeholder graph row on the AA screen, and localized all remaining editor strings.
- Reduced CPU overhead while driving by caching fonts, profiles, and OBD polling settings, and by sharing duplicate EGR responses.
