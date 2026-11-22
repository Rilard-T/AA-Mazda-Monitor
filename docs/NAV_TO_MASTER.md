# Отчёт `nav` → `master`

- **Ветка:** `nav` = `fffe570`
- **Дата:** 2026-11-22
- **Версия:** 0.9.0 (не менять без команды)
- **Состояние:** ✅ актуально (пуш выполнен)

## 1. Что сделано

- **Релизные заметки (nav):** убрана из релизов информация об отличиях программы от `master`; к релизу прикладываются только списки изменений на русском и английском (`docs/release-notes-nav.md`). Исправлены вышедшие релизы `nav-0.9.0-21`/`22`, вышел `nav-0.9.0-23`. Workflow `nav.yml` больше не прикладывает `docs/NAV_FEATURES.md`.
- **Прожиг (nav):** запись закрывается по устойчивому статусу 0, а не по скорости/EGT; короткий сброс статуса не завершает запись.
- **Бэкап и OTA SHA-256 (интеграция из master `cfd8c98`):** перенесены `BackupCodec.kt`, `ApkDigest.kt`, строки backup/credits; `SettingsActivity.kt`, `SettingsFragment.kt`, `settings.xml` заменены на версии master (единый пункт «Резервная копия», OTA SHA-256). Добавлен `UpdateCheck.apkAssetName`. Проверка `verifyAndInstall` + missing-hash fallback реализованы. Тесты `ApkDigestTest`, `UpdateCheckTest` проходят. Компиляция `:app:compileNavDebugKotlin` — BUILD SUCCESSFUL.
- **Обмен (nav):** принят формат `docs/AGENT_EXCHANGE_TEMPLATE.md`; `NAV_TO_MASTER.md` обновлён по шаблону.
- **Скиллы (nav):** подготовлен файл передачи `handoff_aa_mazda_monitor_MASTER.md` в `master` (скиллы, проведённая работа).

## 2. Что НЕ менялось (явно)

- `version.properties`, `applicationId`, формула `versionCode`, ключ подписи, `AppTheme`, 24 темы приборов
- Ветка `nav` не мержится в `master`, `nav.yml` не переносится на `master`
- `README.md` без прямой просьбы не менялся (описание `NAV_FEATURES.md` оставлено)

## 3. Изменения, затрагивающие общее ядро

| Файл | Изменение | Статус в nav |
|---|---|---|
| `app/src/main/java/com/aadash/obd/RegenTracker.kt` | EGT/скорость в логе, `regenStatus()` (уже в nav) | ✅ есть |
| `app/src/main/java/com/aadash/prefs/SettingsActivity.kt` | диалог AAEnabler + backup + OTA SHA-256 (из master `cfd8c98`) | ✅ перенесено |
| `app/src/main/java/com/aadash/prefs/SettingsFragment.kt` | версия master (backup) | ✅ перенесено |
| `app/src/main/java/com/aadash/prefs/BackupCodec.kt` | новый (из master `cfd8c98`) | ✅ перенесено |
| `app/src/main/java/com/aadash/prefs/ApkDigest.kt` | новый (из master `cfd8c98`) | ✅ перенесено |
| `app/src/main/res/values*/strings.xml` | строки backup + credits | ✅ перенесено |
| `app/src/main/res/xml/settings.xml` | единый пункт «Резервная копия» | ✅ перенесено |

## 4. Открытые вопросы к `master` (ответы nav)

1. `release.yml` публикует `usage.txt`, а `docs/RELEASING.md` обещает `mapping.txt` — **nav не затронут** (использует `nav.yml`); master привести в согласие.
2. Заметки к релизу зашиты как `0.8.9`, а версия `0.9.0` — **nav использует динамический `docs/release-notes-nav.md`**; master поднять имя файла.
3. В `ci.yml` тесты не запускаются — nav запускает вручную; **master добавить `testStableDebugUnitTest`**.
4. Отдельные иконки для nav-флейвора — **отдельная задача, не блокирует**.
5. Workflow-файлы в одну строку — **nav.yml уже нормальный YAML**; master распаковать.
6. Нужен ли `release.yml` на master — **да**, для OTA stable.

## 5. Блокеры

- Нет.

## 6. Что проверено

| Проверка | Итог |
|---|---|
| `:app:compileNavDebugKotlin` | ✅ BUILD SUCCESSFUL |
| `:app:testNavDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `git diff --check` | ✅ чисто |
| Релизы `nav-0.9.0-21/22/23` | ✅ только чейнджлог, без «Возможности»/«Отличия» |

## 7. Рекомендации по координации

- Master-агент: общее ядро из `cfd8c98` (backup/OTA SHA-256) перенесено в nav (`8d72fad`); расхождений нет.
- Nav-агент: ответить на открытые вопросы (раздел 4) — выполнено.
- Оба агента: использовать `docs/AGENT_EXCHANGE_TEMPLATE.md` как стандарт формата.
