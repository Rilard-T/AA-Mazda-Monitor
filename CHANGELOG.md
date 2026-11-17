# История версий AA Mazda Monitor

Полные тексты заметок к релизам — в [`docs/`](docs), собранные APK — в
[раздел Releases](https://github.com/Rilard-T/AA-Mazda-Monitor/releases).

Обновление всегда ставится поверх предыдущей версии без удаления: подпись не меняется.

## 0.8.8

Технические подробности; для пользователя — `docs/release-notes-0.8.8.md`.

- `ObdBridgeService`: `START_NOT_STICKY`, `onTaskRemoved` → `stopSelf()`, остановка через `stopService()`; `MAX_CONNECT_ATTEMPTS = 5`, после лимита — `giveUp()`. Сбои связи считаются подряд: `okStreak`, сброс `failures` только после 3 успехов (иначе «мигающая» связь не даёт переподключения).
- `ObdEngine`: `onOk/onFail`, `giveUp()`; опрос медленных датчиков и журнала прожигов вынесен в отдельный поток `obd-regen`.
- `GaugeRefresher`: пакетный опрос — `getPIDValuesAsDouble` одним запросом на экран вместо обхода каждого датчика. `NumberFormat` теперь в полях экземпляра, а не общий статический (гонка между потоками). В `Elm327Link` — отдельный поток-читатель с очередью, в `WifiLink` — `soTimeout`. `GlobalScope` заменён на `lifecycleScope`/`scope`.
- Удалён `requestQuit`, убран `startObd()` из настроек, из манифеста удалены Torque `<queries>` и intent-filter.
- Новый экран `ObdCheckActivity`/`ObdCheckFragment`: опрос всех `MazdaPids` через `ObdBridgeService.peek` каждые 2 с (без блокировки UI, `Executors.newSingleThreadExecutor`), группы по заголовкам 7E0 / 7E1 / расчётные. Флаг `sensor_check_pending` ставится службой после первого успешного `init()`, экран открывается один раз по этому флагу и отмечает `sensor_check_shown`; постоянный вход — «Подключение к OBD».
- Проверка зажигания: `ObdEngine.lastAnswerAt` обновляется при каждом ответе ЭБУ (и при удачном `init()`), `ignitionOn()` = ответ был менее 15 с назад (`IGNITION_HOLD_MS`), `ObdBridgeService.ignitionOn()` возвращает `null` без подключения. Пока зажигание не включено, у датчиков «Ожидание…» вместо «Нет ответа».
- `RegenTracker`: `STATUS_HOLD_MS` 10 с → 30 с для статуса 1/2; отдельный флаг `midSession` — подключение посреди прожига ждёт выдержку, но запись не создаёт; `System.currentTimeMillis()` заменён на инъекцию `clock` для тестов. `RegenTrackerTest`: 9 тестов и `FakePrefs` на `SharedPreferences` (выдержка статуса, импорт/экспорт, сажа, интервал).
- Переименования своих классов: `TorqueService→ObdServiceClient`, `TorqueServiceWrapper→StatsService`, `TorqueData→GaugeData`, `TorqueRefresher→GaugeRefresher`, `TorqueGauge→GaugeFragment` (+`GaugeFragmentKt`), `TorqueChart→ChartFragment`, `TorqueDisplay→DisplayFragment`, `TorqueSpeedometer→SpeedometerView`; `CONNECTING_TORQUE→CONNECTING_SERVICE`, `startTorque→connect`, `torqueService→obdService`, `torqueConnection→serviceConnection`, `torqueRefresher→gaugeRefresher`, `torqueChart→chartFragment`, `torqueBind→boundService`. Обновлены `fragment_dashboard.xml`, `fragment_gauge.xml`, `chart_legend.xml`, манифест, `proguard-rules.pro`.
- Намеренно оставлены для совместимости: префикс `torque_` в DataStore, AIDL `org.prowl.torque.remote.ITorqueService` и его DESCRIPTOR, GPL-кредиты — старые профили, плагины Torque и атрибуция.
- Удалены неполные локали: `values-cs`, `values-de`, `values-el`, `values-es`, `values-fr`, `values-fr-rCA`, `values-it`, `values-nl`, `values-pl`, `values-pt`, `values-pt-rBR`, `values-sl`, `values-uk`, `values-zh-rHK`; остались `values` (английский) и `values-ru`.
- `versionCode 8008`, подпись прежняя; в релиз прилагаются `app-release.apk` и `usage.txt`. Заметки релиза вынесены в `docs/release-notes-0.8.8.md`, workflow берёт файл текущей версии.

## 0.8.7

- Проверка обновлений через перенаправление GitHub вместо API: обход лимита запросов (ошибка 403).

## 0.8.6

- Переработаны настройки: единый экран с разделами, меню из 4 пунктов вместо 11.
- Проверка порога сажи (0,5–20 г/л; 0 — по умолчанию 6,5).
- Выделен парсер журнала прожигов, добавлены тесты.
- Добавлен FAQ в README.

## 0.8.5

- Экспорт и импорт журнала прожигов (меню «⋮»).
- Ошибки проверки обновлений записываются в журнал.

## 0.8.4

- Русский и английский интерфейс.
- Исправлен список PID и лишние линии в настройках в release-сборке.
- Убрано окно «Совместимость приложений Android» при установке.
- Поддержка страниц памяти 16 КБ (Android 15/16).
