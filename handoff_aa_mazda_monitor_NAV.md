# ПЕРЕДАЧА ПРОЕКТА: AA Dash Nav, ветка `nav`

Ты продолжаешь работу за предыдущим ассистентом. Отвечай по-русски. Прочитай раздел «Правила работы» до первого ответа.

## 1. Мета
- Репозиторий: `Rilard-T/AA-Mazda-Monitor`, ветка `nav`.
- Последний коммит: `9484efc feat(nav): аккордеон-редактор раскладки, тема приложения, уведомление прожига, экран «О приложении»`.
- Рабочая копия чистая (всё закоммичено и запушено).
- Пакеты: `com.aadash.*` (namespace `com.aadash.stats`, R из `com.aadash.stats.R`).
- Проект: AA Dash Nav — дашборд на экране Android Auto (Car App Library, навигационный интерфейс), данные из ELM327.

## 2. Сборка
- JDK: `$HOME/tools/jdk`, SDK: `$HOME/android-sdk`.
- Команда: `export JAVA_HOME=$HOME/tools/jdk && export ANDROID_HOME=$HOME/android-sdk && export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH && ./gradlew --no-daemon :app:assembleNavDebug`
- Последняя сборка: BUILD SUCCESSFUL.
- `artifacts/` и `screenshots/` не коммитить.

## 3. Что уже сделано (хронология)
1. Ремонт сломанных тем приборной панели (коммит `845d28c`): 24 темам подставлены `dial_background_blank_*` (без рисок), риски/подписи рисуются программой; `tickLabels` радиус `0.62 r`; название прибора `0.66 r`; мягкая тень у текста. SkodaOneApp: создан `dial_background_blank_skodaone.png`.
2. Уведомление о прожиге: баннер в `NavRenderer` (строка `regen_banner_text`).
3. Экран «О приложении»: обновлён `fragment_credits.xml` (стиль интерфейса, блок «Благодарности»).
4. Тема приложения: режим системная/светлая/тёмная через `AppCompatDelegate` (`app_theme_mode` в settings, `App.kt`, `SettingsFragment.kt`). Акцентный цвет не реализован.
5. Редактор раскладки: варианты визуализации сгенерированы (`artifacts/editor-variant-*.jpg`). Выбран и реализован вариант **A (аккордеон)** — пункты бывшего меню «⋯» перенесены в панель, сгруппированы: «Форма», «Пропорции», «Вид», «Элементы», «Графики», «Инструменты». Меню очищено, остался «Сохранить».

## 4. Ключевые файлы
- `app/src/main/java/com/aadash/nav/NavPainter.kt` — рисование приборов, `themedDial`, `tickLabels`, `gaugeTexts`, тень текста.
- `app/src/main/java/com/aadash/nav/NavTheme.kt` — `NavThemeRes`, `NavThemes.load/names`.
- `app/src/main/java/com/aadash/nav/NavRenderer.kt` — рендеринг, баннер прожига.
- `app/src/main/java/com/aadash/nav/NavEditorActivity.kt` — редактор раскладки (аккордеон).
- `app/src/main/java/com/aadash/nav/NavEditorView.kt` — предпросмотр с касанием.
- `app/src/main/java/com/aadash/nav/NavLayout.kt` / `NavLayoutStore` — модель и хранение раскладки.
- `app/src/main/java/com/aadash/stats/App.kt` — применение темы приложения.
- `app/src/main/res/values/styles.xml` — темы приборов.
- `app/src/main/res/xml/settings.xml`, `arrays.xml`, `strings.xml`, `values-ru/strings.xml` — настройки.

## 5. Очередь задач (делать только по команде пользователя)
1. Визуальная проверка на устройстве/в Android Auto: аккордеон в портрете (прокрутка), панель в альбоме, темы приборов.
2. Акцентный цвет приложения: палитра primary/secondary, сохранение выбора, применение через тему/ресурсы.
3. Проверка остальных тем (сравнение с основным приложением), особенно темы с линией вместо стрелки.
4. Адаптивные пропорции предпросмотра (запоминание реальных пропорций области).
5. Настройка максимума приборов (скорость/обороты) с «круглым» шагом подписей.

## 6. Правила работы
- Отвечать по-русски; честно указывать непроверенное.
- Темы приборов: только исправлять, не менять внешний вид/стиль/палитру.
- `artifacts/`, `screenshots/` не коммитить.
- `README`, `version.properties` не менять.
- Коммит и пуш только по явной команде пользователя.
- Секреты не печатать.
- Не запускать `npx convex login` / интерактивный `convex dev` (не применимо, но правило среды).

## 7. Как проверить
- Сборка: команда из раздела 2.
- Тесты: `./gradlew --no-daemon :app:testNavDebugUnitTest` (если нужны).
- После изменений пересобрать и убедиться в BUILD SUCCESSFUL.