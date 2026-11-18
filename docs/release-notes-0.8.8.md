AA Mazda Monitor 0.8.8

**Русский**

- Исправлено «просыпание» службы: после выхода из приложения и закрытия Android Auto она больше не поднимается сама и не держит адаптер занятым. Автоподключение осталось прежним и по-прежнему делается максимум 5 попыток.
- Приборы в Android Auto обновляются одним запросом на экран вместо отдельного опроса каждого датчика — из-за очереди к ELM327 значения отставали от реальных.
- Устранены редкие «застывания» значений: счётчики форматирования больше не делятся между потоками, чтение с адаптера идёт через отдельный поток с очередью.
- Настройки больше не запускают службу при открытии: подключение к адаптеру — только по кнопке и при старте Android Auto.
- Внутренние имена классов и сообщения журнала больше не путаются с приложением Torque; список датчиков, профили и журнал прожигов сохранены.
- Прожиг считается идущим только после того, как статус блока (1 или 2) держится 30 секунд подряд: случайные всплески статуса больше не включают флаг и не создают запись.
- После первого успешного подключения к адаптеру один раз открывается «Проверка датчиков»: приложение опрашивает все параметры и показывает, какие блоки отвечают, а какие нет. Позже экран доступен в «Подключение к OBD → Проверка датчиков».
- В проверке датчиков есть состояние зажигания: при выключенном зажигании вместо «Нет ответа» показывается «Ожидание» и подсказка включить зажигание; «нет ответа» значит, что блок не поддерживает параметр.
- В интерфейсе остались только полные переводы — русский и английский. Неполные локали (de, es, fr, it, nl, pl, pt, cs, el, uk, sl, zh-rHK) удалены: при этих языках системы приложение показывает английский текст.
- Версия 0.8.8 — ставится поверх 0.8.7 без удаления.

**English**

- Fixed the service waking up on its own: after leaving the app or closing Android Auto it no longer restarts and keeps the adapter busy. Auto-connect is unchanged and still makes at most 5 attempts.
- Gauges in Android Auto now refresh with a single request per screen instead of polling every sensor separately — the ELM327 queue made values lag behind reality.
- Fixed rare frozen readings: number formatters are no longer shared between threads, and adapter reads go through a dedicated reader thread with a queue.
- Settings no longer start the service when opened: the adapter connects only from the button and when Android Auto starts.
- Internal class names and log messages no longer collide with the Torque app; the PID list, profiles and the regen log are preserved.
- A regeneration is shown as running only after the unit status (1 or 2) stays non-zero for 30 seconds in a row; short status spikes no longer set the flag or create a record.
- After the first successful adapter connection, the “Sensor check” screen opens once: the app polls every parameter and shows which ones the unit answers and which it does not. It is also available later from “OBD connection → Sensor check”.
- The sensor check shows the ignition state: with the ignition off it says “Waiting” and asks to turn the ignition on instead of “No answer”; “No answer” then means the unit does not support that parameter.
- Only complete translations remain — Russian and English. Incomplete locales (de, es, fr, it, nl, pl, pt, cs, el, uk, sl, zh-rHK) were removed; with those system languages the app falls back to English.
- Version 0.8.8 — installs over 0.8.7 without uninstalling.
