# Отчёт master → nav

- **Ветка:** `master` = `effc0cb`
- **Дата:** 2026-11-21
- **Версия:** 0.9.0 (не менять без команды)
- **Состояние:** ✅ актуально (пуш выполнен)

## 1. Что сделано
- **Backup (единый пункт):** объединены экспорты/импорты настроек, журнала прожига, дашбордов в один пункт «Резервная копия» (`settings.xml`, `SettingsFragment.kt`, `SettingsActivity.kt`) — коммит `effc0cb`
- **BackupCodec:** чистые функции `encode()`/`decode()` с заголовком `AA_MONITOR_BACKUP;1`, base64-настройки + текст журнала — `BackupCodec.kt` (новый)
- **ApkDigest:** SHA-256 вычисление, разбор `sha256sum`, сравнение без регистра — `ApkDigest.kt` (новый)
- **OTA SHA-256:** проверка хэша при обновлении (`verifyAndInstall()`); пропуск, если `.sha256` отсутствует (совместимость 0.9.0) — `SettingsActivity.kt`
- **release.yml:** добавлен шаг `sha256sum app-release.apk > app-release.apk.sha256` + загрузка в релиз
- **Тесты:** `BackupCodecTest` (5) + `ApkDigestTest` (6) — чистые JVM-функции
- **Общие улучшения (395841d):** потокобезопасный кэш ObdEngine (`lastEgt`), EGT/скорость в RegenTracker, убран StatusBarController, версия+build в Credits, диалог AAEnabler при OTA
- **Документация:** `docs/AGENT_EXCHANGE_TEMPLATE.md` — утверждённый формат обмена

## 2. Что НЕ менялось (явно)
- `version.properties` (0.9.0), `applicationId`, формула `versionCode`, ключ подписи, `AppTheme`
- `README.md` (обновляется nav-агентом)
- Ветка `nav` не мержилась в `master`, `nav.yml` не переносился

## 3. Изменения, затрагивающие общее ядро
| Файл | Изменение | Нужно в nav? |
|---|---|---|
| `app/src/main/java/com/aadash/obd/ObdEngine.kt` | потокобезопасный кэш, `lastEgt` | да |
| `app/src/main/java/com/aadash/obd/RegenTracker.kt` | EGT/скорость в логе, `regenStatus()` | да |
| `app/src/main/java/com/aadash/stats/CarFragment.kt` | убран StatusBarController | да |
| `app/src/main/java/com/aadash/stats/DashboardFragment.kt` | убран StatusBarController | да |
| `app/src/main/java/com/aadash/stats/CreditsFragment.kt` | версия + build number | да |
| `app/src/main/java/com/aadash/stats/LogExport.kt` | имя приложения из ресурсов | да |
| `app/src/main/java/com/aadash/prefs/SettingsActivity.kt` | диалог AAEnabler + backup + OTA SHA-256 | да (backup/OTA) |
| `app/src/main/res/values*/strings.xml` | строки backup + credits | да (backup) |

## 4. Открытые вопросы к nav
1. `release.yml` публикует `usage.txt`, а `docs/RELEASING.md` обещает `mapping.txt` — привести в согласие?
2. Заметки к релизу зашиты как `0.8.9`, а версия `0.9.0` — поднять имя файла или сделать динамическим?
3. В `ci.yml` тесты не запускаются — добавить `testStableDebugUnitTest`?
4. Отдельные иконки для nav-флейвора (сейчас побайтово равны stable)?
5. Workflow-файлы в одну строку — распаковать в нормальный YAML?
6. Нужен ли `release.yml` на master, если nav-канал им не пользуется?

## 5. Блокеры
- **Пуш:** ✅ Выполнен. Remote `origin/master` = `cfd8c98`. Использован PAT с scope `repo` + `workflow` (из секретов проекта).
- **Примечание для nav-агента:** формат обмена файлами утверждён (`docs/AGENT_EXCHANGE_TEMPLATE.md`). При обновлении `NAV_TO_MASTER.md` используйте новый формат (7 разделов + метаданные в шапке).

## 6. Что проверено
| Проверка | Итог |
|---|---|
| `:app:testStableDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `:app:compileNavDebugKotlin` | ✅ BUILD SUCCESSFUL (2m12s) |

## 7. Рекомендации по координации
- Nav-агент: перенести изменения общего ядра (раздел 3) в ветку nav через cherry-pick или ручной перенос.
- Nav-агент: ответить на открытые вопросы (раздел 4) в `NAV_TO_MASTER.md`.
- Владелец: обновить GitHub-токен со scope `repo,workflow` для разблокировки пуша.
- Оба агента: использовать `docs/AGENT_EXCHANGE_TEMPLATE.md` как стандарт формата.
