# Передача ветки `master` — AA-Mazda-Monitor

Дата: 18.11.2026. Репозиторий: `Rilard-T/AA-Mazda-Monitor`, лицензия GPL-3.0.
Документ для агента/разработчика, который продолжит работу с веткой **`master`** (стабильное приложение).
После разделения в репозитории два самостоятельных приложения, а не «версии одного».

---

## 1. Два приложения

| | stable | nav |
|---|---|---|
| Ветка | `master` | `nav` (заведена 18.11.2026 из `beta`; `beta` — архив) |
| Название | `AA Mazda Monitor` | `AA Dash Nav` |
| applicationId | `com.aamazda.monitor` | `com.aadash.nav` |
| Gradle-флейвор | `stable` | `nav` |
| Android Auto | legacy projection: `CarService.java`, `MainCarActivity.kt`, `lib/aauto.aar`, `automotive_app_desc` = `projection`+`service` | `androidx.car.app` templates: пакет `com/aadash/nav/*`, `NavCarAppService`, `automotive_app_desc` = `template` |
| Уникальные фичи | — | редактор раскладки приборов, профили автомобилей (`AppProfiles`), динамические prefs |
| Релиз | тег `X.Y.Z`, workflow `release.yml`, ручной запуск с `master` | тег `nav-X.Y.Z-<run>`, prerelease, workflow `nav.yml` (по пушу в `beta`/`nav`) |

Kotlin-пакеты и `namespace` общие для обоих приложений: `com.aadash.*` (`namespace com.aadash.stats`).
Имя `com.aatorque.*` из форка выведено полностью; держать его в `master` нельзя — ветки разъедутся.
`-keep` в `proguard-rules.pro` теперь `class com.aadash.**`.

Общее ядро: `app/src/main/java/com/aadash/obd/` (22 файла) и телефонный UI. На 18.11.2026 OBD-код
ветвей отличается 13 строками в 5 файлах — только вызовами `AppProfiles.*PrefsName(...)`.
Удачные правки OBD нужно переносить в **обе** ветки, иначе фичи разъедутся.

## 2. Состояние на 18.11.2026

* `master` = `4a92225` (`ci(release): обновить actions до актуальных мажоров`), `version.properties` =
  0.8.9. Флейворов, брендового слоя, переименования `com.aatorque` и CI под флейворы там **нет**.
* `beta` = `0ca4fce` + коммит «nav-переименование»: флейворы, брендовый слой, `com.aadash.*`,
  `AA Dash Nav`. Из этого коммита заведена релизная ветка **`nav`** (см. §9).
* `nav` — релизная ветка nav-приложения (`nav.yml`, теги `nav-X.Y.Z-<run>`); `beta` после переезда
  остаётся архивом.
* Направление обычных мержей: `master` → `nav`. Ветку `nav` в `master` не мержить: там другой
  продукт (nav-интерфейс), который сломает стабильный экран Android Auto.

## 3. Список задач для ветки `master`

Выполнять по порядку; после каждой задачи — сборка `assembleStableDebug`, после блока ресурсов —
ещё и `assembleStableRelease`.

- [ ] **M0. Переименовать пакеты `com.aatorque` → `com.aadash`.** Пакеты уже переименованы в `beta`,
      поэтому на `master` это делается ровно так же, иначе ветки разъедутся на 85 файлах:
      `git mv app/src/main/java/com/aatorque app/src/main/java/com/aadash` (и то же в `src/test`),
      затем `s/\bcom\.aatorque/com.aadash/g` по `.kt`, `.java`, `.xml`, `AndroidManifest.xml`,
      `proguard-rules.pro` (`-keep class com.aadash.**`) и `build.gradle` (`namespace com.aadash.stats`),
      плюс `docs/`. `applicationId` от namespace не зависит и не меняется.
- [ ] **M1. Забрать брендовый слой из `beta`.** Черри-пик/ручной перенос коммита с флейворами
      (файлы — §4). Конфликты в `app/build.gradle` и `strings.xml` разрешать в пользу брендового
      слоя, проверяя отсутствие дублей ключей.
- [ ] **M2. Флейворы в `app/build.gradle`.** `flavorDimensions "channel"`, `stable`
      (`com.aamazda.monitor`) и `nav` (`com.aadash.nav`, `versionNameSuffix "-nav[.<build>]"`),
      брендовый `RELEASE_URL`, `OTA_ENABLED=false`, `app_version` через `variant.resValue`.
      `applicationId` из `defaultConfig` и старые `resValue` из buildTypes удалить.
- [ ] **M3. Брендовые ресурсы.** `app/src/stable/res/values{,-ru}/brand.xml`,
      `app/src/stable/res/values/colors.xml` (Mazda red), иконки `app/src/stable/res/mipmap-*`;
      для структурного близнеца — `app/src/nav/res/values{,-ru}/brand.xml` («AA Dash Nav»)
      и иконки с меткой β. Брендовые строки (`app_name`, `activity_main_title`,
      `activity_settings_title`, `obd_notif_title`, `credits_fork_notice`, `log_file_prefix`)
      из `main` удалены — не возвращать.
- [ ] **M4. Иконки.** `app/src/main/res/mipmap-*` удалены; иконки живут только во флейворах.
      Новый флейвор без своих mipmap не соберётся.
- [ ] **M5. Версия приложения в коде.** `LogExport` берёт имя приложения и префикс файла
      из ресурсов (`R.string.app_name`, `R.string.log_file_prefix`) — сохранить эту правку,
      иначе в журнале nav-приложения печатается старое имя.
- [ ] **M6. CI под флейворы.** `release.yml` → `assembleStableRelease`, APK
      `app/build/outputs/apk/stable/release/*.apk`, proguard
      `app/build/outputs/mapping/stableRelease/usage.txt`; `pr-checks.yml` →
      `testStableDebugUnitTest testNavDebugUnitTest lintNavDebug`; `build.yml` → `apk/*/debug/*.apk`;
      `ci.yml` → `lintStableRelease`, `assembleStableRelease`, пути `apk/stable/release/…`
      и `mapping/stableRelease/mapping.txt`. `nav.yml` — только в `beta`, не переносить.
- [ ] **M7. Проверка сборки и манифеста.** `aapt dump badging`: `name='com.aamazda.monitor'`,
      `versionName` без суффикса, `application-label:'AA Mazda Monitor'`; FileProvider
      `android:authorities="${applicationId}.logfileprovider"` — оставить параметризованным.
- [ ] **M8. Релиз stable.** Версию и заметки согласовать с владельцем, `version.properties`
      менять только по его команде; затем Actions → `release` → Run workflow с `master`.
      Проверить APK + `mapping/stableRelease/usage.txt`, установку поверх 0.8.9 и «Проверить обновления».
- [ ] **M9. Документация.** `docs/RELEASING.md` (пути `apk/stable/release`, `mapping/stableRelease`),
      `CHANGELOG.md`, при необходимости README. `AGENT_CLAIMS.md` — решение владельца.

На `master` флейвор `nav` — структурный близнец для чистых мержей: собирать и публиковать его оттуда
нельзя (в `master` нет nav-интерфейса, получится «Dash Nav» без главной фичи).

## 4. Что именно переносится (файлы)

| Файл/каталог | Что там |
|---|---|
| `app/build.gradle` | флейворы, id, `versionNameSuffix`, `RELEASE_URL`/`OTA_ENABLED`/`REPO_URL`, `app_version` |
| `app/src/stable/res/values{,-ru}/brand.xml` | brand-токены stable |
| `app/src/stable/res/values/colors.xml` | акцент Mazda red |
| `app/src/stable/res/mipmap-*` | 5 плотностей × обычная/круглая |
| `app/src/nav/res/values{,-ru}/brand.xml` | brand-токены nav |
| `app/src/nav/res/mipmap-*` | иконки nav (с меткой β) |
| `app/src/main/res/values{,-ru}/strings.xml` | брендовые ключи удалены |
| `app/src/main/res/mipmap-*` | удалены |
| `app/src/main/java/com/aadash/stats/LogExport.kt` | имя приложения и префикс лога из ресурсов |
| `app/src/main/java/com/aadash/**` (было `com/aatorque/**`) | переименование namespace — задача M0 |
| `.github/workflows/{release,pr-checks,build,ci}.yml` | задачи и пути под флейворы |

## 5. Проверки

```
export JAVA_HOME="$HOME/tools/jdk"; export PATH="$JAVA_HOME/bin:$PATH"; export ANDROID_HOME="$HOME/android-sdk"
./gradlew --no-daemon :app:assembleStableDebug :app:testStableDebugUnitTest
./gradlew --no-daemon :app:assembleStableRelease   # нужен KEYSTORE_FILE/PASSWORD/KEY_ALIAS
$ANDROID_HOME/build-tools/34.0.0/aapt dump badging app/build/outputs/apk/stable/release/app-stable-release.apk
```

Ожидаемо: `package: name='com.aamazda.monitor' versionName='0.8.9'`, `application-label:'AA Mazda Monitor'`.
Для nav: `name='com.aadash.nav' versionName='0.8.9-nav'`, `application-label:'AA Dash Nav'`.

## 6. Ограничения (соблюдать без напоминаний)

* Не менять `AppTheme`/темы (30 стилей в `values/styles.xml`) — только по отдельной просьбе.
* Не менять ключ подписи и не перевыпускать сертификат (`docs/RELEASING.md`).
* `applicationId` и формула `versionCode` (`major*100000 + minor*1000 + patch`) — не менять.
* `version.properties` не трогать без явной команды; `artifacts/`, `build/`, keystore не коммитить.
* Force-push, переписывание истории, удаление тегов — только по явной команде владельца.
* Секреты не печатать и не писать в файлы. Ответы и документацию писать по-русски.

## 7. Известные грабли

1. **Конфликты мержа** в `app/build.gradle` и `res/values*/strings.xml` между `master` и `beta`
   неизбежны, пока брендовый слой не перенесён на `master`.
2. **`AGENT_CLAIMS.md`** есть в `beta`, удалён в `master` — решение владельца.
3. **`versionNameSuffix`**: `debug` перекрывает флейворный суффикс (`0.8.9.debug`); в release
   всё корректно.
4. **`lib/aauto.aar`** нужен только stable; в `beta` его нет — не «чинить» это при мерже.
5. **Смена applicationId nav-приложения** (`com.aamazda.monitor.beta` → `com.aadash.nav`)
   означает, что старая бета не обновится поверх: тестировщикам нужно удалить её и поставить заново.

## 8. Что из `beta` на `master` переносить не нужно

`com/aadash/nav/*`, `AppProfiles.kt`, `ProfilesActivity.kt`, `res/values*/strings_nav.xml`,
`design/aa-nav-screens-mockup.html`, `docs/PLAN_NAV.md`, `RULES.md`, `docs/HANDOFF.md`,
`docs/PID_PROFILES.md`, `handoff_aa_mazda_monitor_BETA_nav.md` — это жизнь nav-приложения.

## 9. Как выпускается nav-приложение (для контекста master-агенту)

* Релизы идут из ветки `nav`: пуш в неё запускает `nav.yml` → `assembleNavRelease` →
  prerelease `nav-X.Y.Z-<run>` с APK `apk/nav/release/app-nav-release.apk`.
* `version.properties` общий для обоих приложений; bump `buildNumber` делает сам CI (`GITHUB_RUN_NUMBER`).
* applicationId `com.aadash.nav` сменился с `com.aamazda.monitor.beta`: старые беты не обновятся
  поверх, тестировщикам нужно удалить и поставить заново.

## 10. Открытые вопросы к владельцу

* Оставлять ли `beta` архивом или удалить после стабилизации `nav`.
* Версия stable-релиза после переноса бренда: 0.8.10 или 0.9.0?
* Оставлять ли красный акцент stable (`app/src/stable/res/values/colors.xml`)?
* Судьба `AGENT_CLAIMS.md` и исторических handoff-файлов.
