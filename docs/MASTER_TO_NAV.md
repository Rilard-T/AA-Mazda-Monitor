# Отчёт master → nav

Коммит в master: `eab9436` (`refactor: M0-M9 brand layer, flavors, CI updates`), поверх `9339476` (`chore(release): bump version to 0.9.0`).

## Что сделано в master (M0–M9)

- **M0:** `com.aatorque` → `com.aadash` во всём коде, манифесте, proguard, тестах.
- **M1/M2:** добавлены флейворы `stable`/`nav` (`flavorDimensions "channel"`), брендовый слой в `app/build.gradle`.
- **M3/M4:** созданы `app/src/stable` и `app/src/nav` (`brand.xml`, `values-ru`, stable `colors.xml`); mipmap перенесены во флейворы; брендовые строки удалены из `main`.
- **M5:** `LogExport` использует `R.string.app_name` и `R.string.log_file_prefix`.
- **M6:** workflows обновлены под флейворы; дополнительно исправлен `ci.yml`.
- **M9:** путь mapping в `docs/RELEASING.md` исправлен.
- Версия остаётся `0.9.0`.

## Замечания/риски для nav

- Сборка и тесты **не запускались** — нет JDK/Android SDK. Команды:
  ```bash
  export JAVA_HOME="$HOME/tools/jdk"; export PATH="$JAVA_HOME/bin:$PATH"; export ANDROID_HOME="$HOME/android-sdk"
  ./gradlew --no-daemon :app:assembleStableDebug :app:testStableDebugUnitTest :app:testNavDebugUnitTest
  ./gradlew --no-daemon :app:assembleStableRelease
  $ANDROID_HOME/build-tools/34.0.0/aapt dump badging app/build/outputs/apk/stable/release/app-stable-release.apk
  ```
- `nav`-иконки пока копии stable без метки β — при необходимости заменить.
- `activity_main_title` в `brand.xml` не используется в коде, но оставлен как часть брендового набора.
- `OTA_ENABLED` удалён (не использовался) — если нужен OTA, вернуть.
- `nav`-флейвор в master — структурный близнец; UI/экраны nav не переносились и должны оставаться в ветке nav.
- Не переносить `nav.yml` на master и не мержить nav в master.