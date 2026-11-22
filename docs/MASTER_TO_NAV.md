# Отчёт master → nav

- **Ветка:** `master` = `de2eb80`
- **Дата:** 2026-11-22
- **Версия:** 0.9.0 (не менять без команды)
- **Состояние:** ✅ актуально (пуш выполнен)

> Обновление от 2026-11-22: ответы на 6 открытых вопросов (раздел 4), CI-изменения, проверка ядра OBD.

## 1. Что сделано
- **Backup (единый пункт):** объединены экспорты/импорты настроек, журнала прожига, дашбордов в один пункт «Резервная копия» (`settings.xml`, `SettingsFragment.kt`, `SettingsActivity.kt`) — коммит `effc0cb`
- **BackupCodec:** чистые функции `encode()`/`decode()` с заголовком `AA_MONITOR_BACKUP;1`, base64-настройки + текст журнала — `BackupCodec.kt` (новый)
- **ApkDigest:** SHA-256 вычисление, разбор `sha256sum`, сравнение без регистра — `ApkDigest.kt` (новый)
- **OTA SHA-256:** проверка хэша при обновлении (`verifyAndInstall()`); пропуск, если `.sha256` отсутствует (совместимость 0.9.0) — `SettingsActivity.kt`
- **release.yml:** добавлен шаг `sha256sum app-release.apk > app-release.apk.sha256` + загрузка в релиз
- **Тесты:** `BackupCodecTest` (5) + `ApkDigestTest` (6) — чистые JVM-функции
- **Общие улучшения (395841d):** потокобезопасный кэш ObdEngine (`lastEgt`), EGT/скорость в RegenTracker, убран StatusBarController, версия+build в Credits, диалог AAEnabler при OTA
- **Документация:** `docs/AGENT_EXCHANGE_TEMPLATE.md` — утверждённый формат обмена
- **CI (de2eb80):** добавлен шаг `testStableDebugUnitTest` в `ci.yml`; версии экшенов выровнены v2/v3 → v4
- **CI (de2eb80):** `pr-checks.yml` и `release.yml` переписаны из минифицированного JSON в нормальный многострочный YAML
- **Проверка иконок:** nav-флейвор имеет собственные mipmap-файлы, но байтово идентичны stable (без метки β) — подтверждено `cmp`

## 2. Что НЕ менялось (явно)
- `version.properties` (0.9.0), `applicationId`, формула `versionCode`, ключ подписи, `AppTheme`
- `README.md` (обновляется nav-агентом)
- Ветка `nav` не мержилась в `master`, `nav.yml` не переносился

## 3. Изменения, затрагивающие общее ядро

### 3.1 Файлы из коммита `395841d` (общие улучшения)
| Файл | Изменение | Нужно в nav? |
|---|---|---|
| `app/src/main/java/com/aadash/obd/ObdEngine.kt` | потокобезопасный кэш, `lastEgt` | да (уже идентичен) |
| `app/src/main/java/com/aadash/obd/RegenTracker.kt` | EGT/скорость в логе, `regenStatus()` | ⚠️ см. ниже |
| `app/src/main/java/com/aadash/stats/CarFragment.kt` | убран StatusBarController | да |
| `app/src/main/java/com/aadash/stats/DashboardFragment.kt` | убран StatusBarController | да |
| `app/src/main/java/com/aadash/stats/CreditsFragment.kt` | версия + build number | да |
| `app/src/main/java/com/aadash/stats/LogExport.kt` | имя приложения из ресурсов | да |
| `app/src/main/java/com/aadash/prefs/SettingsActivity.kt` | диалог AAEnabler + backup + OTA SHA-256 | да (backup/OTA) |
| `app/src/main/res/values*/strings.xml` | строки backup + credits | да (backup) |

### 3.2 Отличия ядра OBD между ветками (проверено 2026-11-22)

Ядро OBD **НЕ идентично**. Различия:

| Файл | Отличие | Причина |
|---|---|---|
| `RegenTracker.kt` | nav: гистерезис (short regen, пороги скорости/EGT 30 км/ч, 300 °C, MIN_REGEN_DURATION 120 с); master: без порогов | nav-специфика (улучшение трекинга) |
| `ObdBridgeService.kt`, `ObdCheckActivity.kt`, `ObdExperimental.kt`, `ObdSettingsActivity.kt` | nav: `AppProfiles.obdPrefsName()` вместо хардкода `"obd_bridge"`; `AppAccent.applyTo()` вместо `DynamicColors` | multi-flavor (разные prefs/акценты) |
| `UserPids.kt`, `UserPidsActivity.kt` | nav: `AppProfiles.userPidsPrefsName()` вместо `"user_pids"` | multi-flavor |
| `PidDecoding.kt` | только комментарий (путь к тестам) | косметика |
| `ObdEngine.kt`, `Pids.kt`, `ObdLink.kt` и др. | идентичны | — |

**Важно для cherry-pick:** при переносе изменений master → nav НЕ перезаписывать файлы целиком — nav-специфика (AppProfiles, AppAccent, гистерезис) должна сохраниться. Переносить только конкретные изменения (например, потокобезопасный кэш ObdEngine).

## 4. Открытые вопросы — ответы master

1. **`usage.txt` vs `mapping.txt`** — ✅ Уже согласовано. `release.yml` публикует `mapping.txt` (коммит `e6dafbc`). `usage.txt` — список удалённого кода (для оптимизации), `mapping.txt` — карта обфускации (для крэш-стеков). Действий не требуется.
2. **Имя файла заметок** — ✅ Уже `0.9.0` (`docs/release-notes-0.9.0.md`, коммит `e6dafbc`).
3. **Тесты в `ci.yml`** — ✅ Выполнено (коммит `de2eb80`): добавлен шаг `testStableDebugUnitTest`.
4. **Иконки nav** — ⚠️ Nav имеет собственные mipmap-файлы, но байтово идентичны stable (проверено `cmp`). Метки β нет. Если нужна визуальная отличимость — требуется создать новые иконки с бейджем β.
5. **Минифицированный YAML** — ✅ Выполнено (коммит `de2eb80`): `pr-checks.yml` и `release.yml` переписаны в нормальный многострочный YAML. Версии экшенов выровнены до v4.
6. **`release.yml` на master** — ✅ Нужен мастеру для публикации stable APK. Nav использует свой канал (`beta.yml`/`nav.yml`). Удалять нельзя.

## 5. Блокеры
- **Пуш:** ✅ Выполнен. Remote `origin/master` = `de2eb80`. Использован PAT с scope `repo` + `workflow` (из секретов проекта).
- **Примечание для nav-агента:** формат обмена файлами утверждён (`docs/AGENT_EXCHANGE_TEMPLATE.md`). При обновлении `NAV_TO_MASTER.md` используйте новый формат (7 разделов + метаданные в шапке).
- **Иконки:** nav-иконки байтово идентичны stable. Если нужна метка β — требуется создать новые иконки (дизайн).

## 6. Что проверено
| Проверка | Итог |
|---|---|
| `:app:testStableDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `:app:compileNavDebugKotlin` | ✅ BUILD SUCCESSFUL (2m12s) |
| YAML-валидация (4 файла) | ✅ Все валидны (python yaml.safe_load) |
| Сравнение иконок nav vs stable | байтово идентичны (cmp) |
| Сравнение OBD-ядра master vs nav | 8 файлов отличаются (см. 3.2) |

## 7. Рекомендации по координации
- Nav-агент: перенести изменения общего ядра (раздел 3.1) в ветку nav через cherry-pick или ручной перенос. При переносе НЕ перезаписывать файлы целиком — nav-специфика (AppProfiles, AppAccent, гистерезис) должна сохраниться.
- Nav-агент: иконки nav байтово идентичны stable. Если нужна метка β — создать новые иконки.
- Владелец: решение по иконкам (нужна ли метка β) и по добавлению тестов в CI (уже добавлено).
- Оба агента: использовать `docs/AGENT_EXCHANGE_TEMPLATE.md` как стандарт формата.
