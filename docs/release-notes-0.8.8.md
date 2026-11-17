AA Mazda Monitor 0.8.8

**Русский**

- Служба подключения больше не «просыпается» сама: после выхода из приложения и закрытия Android Auto она не поднимается и не занимает адаптер. Автоподключение осталось прежним — не больше 5 попыток.
- Приборы в Android Auto обновляются ровнее: значения больше не отстают от реальных.
- Убраны редкие «застывания» показаний.
- Настройки больше не подключаются к адаптеру при открытии: связь появляется по кнопке «Подключиться» и при запуске Android Auto.
- Новое — «Проверка датчиков». Открывается после первого успешного подключения, а позже доступна в «Подключение к OBD». Показывает, какие параметры ваша машина отдаёт, а какие нет, и включено ли зажигание: при выключенном зажигании вместо «Нет ответа» будет подсказка его включить.
- Прожиг теперь считается начавшимся, только если блок сообщает о нём 30 секунд подряд: случайные всплески статуса больше не создают запись в журнале.
- Внутренние названия и сообщения журнала больше не путаются с приложением Torque. Список датчиков, профили и журнал прожигов сохранены.
- В интерфейсе остались только полные переводы — русский и английский; на других языках системы приложение показывает английский текст.
- Версия 0.8.8 — ставится поверх 0.8.7 без удаления.

**English**

- The connection service no longer wakes up on its own: after leaving the app or closing Android Auto it does not restart and does not keep the adapter busy. Auto-connect is unchanged — at most 5 attempts.
- Gauges in Android Auto update more smoothly: values no longer lag behind reality.
- Rare frozen readings are gone.
- Settings no longer connect to the adapter when opened: the connection appears from the “Connect” button and when Android Auto starts.
- New “Sensor check”. It opens after the first successful connection and is available later from “OBD connection”. It shows which parameters your car reports and which it does not, plus whether the ignition is on: with the ignition off you get a hint to turn it on instead of “No answer”.
- A regeneration now counts as started only when the unit reports it for 30 seconds in a row: short status spikes no longer create a log entry.
- Internal names and log messages no longer collide with the Torque app. The PID list, profiles and the regeneration log are preserved.
- Only complete translations remain — Russian and English; with other system languages the app shows English.
- Version 0.8.8 — installs over 0.8.7 without uninstalling.
