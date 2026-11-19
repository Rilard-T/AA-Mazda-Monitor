# ПЕРЕДАЧА: AA Dash Nav, ветка `nav`

Отвечай по-русски. Прочитай «Правила» до ответа.

## Мета
- Репозиторий `Rilard-T/AA-Mazda-Monitor`, ветка `nav`.
- Последний коммит: `c13cc73` (handoff). Рабочая копия чистая.
- Пакеты `com.aadash.*`. Дашборд на Android Auto (Car App Library), данные из ELM327.

## Сборка
```
export JAVA_HOME=$HOME/tools/jdk && export ANDROID_HOME=$HOME/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
./gradlew --no-daemon :app:assembleNavDebug
```
Последняя: BUILD SUCCESSFUL. `artifacts/`, `screenshots/` не коммитить.

## Сделано
1. Ремонт тем приборной панели (`845d28c`): 24 темам — `dial_background_blank_*` (без рисок), риски/подписи рисуются программой; `tickLabels` radius `0.62 r`, название `0.66 r`, тень текста. SkodaOneApp: `dial_background_blank_skodaone.png`.
2. Баннер прожига в `NavRenderer` (`regen_banner_text`).
3. Экран «О приложении»: `fragment_credits.xml` (стиль интерфейса, блок «Благодарности»).
4. Тема приложения: светлая/тёмная/системная через `AppCompatDelegate` (`app_theme_mode`, `App.kt`, `SettingsFragment.kt`). Акцент не реализован.
5. Редактор раскладки: вариант A (аккордеон) — пункты меню «⋯» перенесены в панель, группы: Форма, Пропорции, Вид, Элементы, Графики, Инструменты. Меню очищено, остался «Сохранить».

## Ключевые файлы
- `nav/NavPainter.kt` — рисование, `themedDial`, `tickLabels`, `gaugeTexts`, тень.
- `nav/NavTheme.kt` — `NavThemeRes`, `NavThemes.load/names`.
- `nav/NavRenderer.kt` — рендеринг, баннер.
- `nav/NavEditorActivity.kt` — редактор (аккордеон).
- `nav/NavEditorView.kt` — предпросмотр с касанием.
- `nav/NavLayout.kt` / `NavLayoutStore` — модель/хранение раскладки.
- `stats/App.kt` — тема приложения.
- `res/values/styles.xml` — темы приборов.
- `res/xml/settings.xml`, `arrays.xml`, `strings.xml`, `values-ru/strings.xml`.

## Очередь (только по команде)
1. Визуальная проверка на устройстве/AA: аккордеон в портрете/альбоме, темы приборов.
2. Акцентный цвет приложения (палитра, сохранение, применение).
3. Сравнение остальных тем с основным приложением (особенно `HalfLineIndicator`).
4. Адаптивные пропорции предпросмотра.
5. Настройка максимума приборов (скорость/обороты) + круглый шаг подписей.

## Правила
- Не менять внешний вид/стиль/палитру тем приборов — только чинить.
- `README`, `version.properties` не менять.
- Коммит/пуш только по явной команде.
- Секреты не печатать.
- Честно указывать непроверенное.

## Проверка
- Сборка: команда выше.
- Тесты: `./gradlew --no-daemon :app:testNavDebugUnitTest`.
- После правок пересобрать, убедиться BUILD SUCCESSFUL.