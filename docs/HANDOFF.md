# HANDOFF — AA Mazda Monitor (beta)

Дата: 2026-11-16. Ветка: `beta`. Последний коммит: `10c04d9`.

## Текущее состояние
- Android Auto NAV полностью реализован (редактор раскладки на телефоне, рендер в машине).
- Медиа-трек из MediaSession (`NavItem.MEDIA`, `NavCarAppService`).
- Выбор шрифта в редакторе (пункт «Шрифт…»), хранится в `NavLayout.font`.
- Красная зона оборотов: шкала 0..8, красное с 6 (миграция 6→8 в `NavLayout.parse`).
- Риски: для стандартной шкалы 0..8 — родной циферблат темы; для 0..6/0..180 — пустой циферблат + плотное кольцо рисок (основные + средние + маленькие), как на приборной панели.
- Проверки: `assembleRelease` и `lintRelease` зелёные.

## Активные задачи / открытые вопросы
- Проверить новый билд beta на устройстве (риски, красная зона, шрифт).
- Медиа-трек работает только при включённом доступе к уведомлениям для AA Monitor на телефоне — проверить на устройстве.
- P2-профили PID (Torque-стиль): `PidProbe`, фоновый `probePids` в `ObdEngine.init`.

## Важные ограничения
- `version.properties` менять только по команде.
- Пуш в `beta` только по явной команде.
- `artifacts/` не коммитить.
- Секреты не печатать.
- `AppTheme`/темы и README не менять без просьбы.
- Ответы по-русски, честно про непроверенное на устройстве.

## Ключевые файлы
- `app/src/main/java/com/aatorque/nav/NavPainter.kt` — отрисовка приборов, риски, красная зона.
- `app/src/main/java/com/aatorque/nav/NavLayout.kt` — модель раскладки, миграция max оборотов.
- `app/src/main/java/com/aatorque/nav/NavEditorActivity.kt` — редактор, пункты «Тема…», «Шрифт…», «Красная зона…», «Максимум…».
- `app/src/main/java/com/aatorque/nav/NavRenderer.kt` — применение темы/шрифта.
- `app/src/main/java/com/aatorque/nav/NavFonts.kt` — маппинг имени шрифта → Typeface.
- `app/src/main/res/values/styles.xml` — темы (dialTicks/dialEmpty).

## Сборка
```
export JAVA_HOME="$HOME/tools/jdk"; export PATH="$JAVA_HOME/bin:$PATH"; export ANDROID_HOME="$HOME/android-sdk"
./gradlew --no-daemon :app:assembleRelease :app:lintRelease
```

## Коммиты (не запушены, если не указано)
- `10c04d9` fix(nav): выровнять цифры по рискам (цифра на 5-й риске)
- `d48be63` fix(nav): утолщить риски и выровнять цифры
- `a0acdb6` fix(nav): не дублировать риски на темах с родным циферблатом
- `6765a2d` fix(nav): плотное кольцо рисок как на приборной панели
- `5a4169c` fix(nav): всегда рисовать риски поверх циферблата
- `d699564` fix(nav): выровнять риски и красную зону со шкалой прибора
- `3c8de8f` fix(nav): сохранять родные риски темы при любой шкале
- `81a1b72` fix(nav): риски при нестандартной шкале; выбор шрифта в редакторе
- `e767f09` feat(nav): элемент «Медиа» с названием играющего трека
- `bc733a2` chore(settings): убрать настройки старого телефонного дашборда
```

Commit locally. Ask push.<|end|><|start|>-start to=run_terminal_command<|channel|>analysis <|constrain|>xml<|message|><rfl:parameter name="command">
cd /workspace/chats/70a0b7ffb03901662323082d; git add docs/HANDOFF.md; GIT_AUTHOR_NAME="Freebuff Agent" GIT_AUTHOR_EMAIL="agent@freebuff.local" GIT_COMMITTER_NAME="Freebuff Agent" GIT_COMMITTER_EMAIL="agent@freebuff.local" git commit -m "docs: файл передачи для нового чата" 2>&1 | tail -3; echo "EXIT=${PIPESTATUS[0]}"