# Передача агенту `master`: кнопка установки с Play-источником при OTA и бэкап с приборами

Дата: 27.11.2026. Канал NAV уже сделал это; здесь — как повторить в `master` (пакет `com.aamazda.monitor`), без слияния веток.

## 0. Контекст

- `nav` удалил AAEnabler и ставит APK напрямую с `EXTRA_INSTALLER_PACKAGE_NAME=com.android.vending`; `master` пока ставит через AAEnabler (`installDownloaded` → `aaEnablerPackage`).
- `nav` бэкап v2 хранит раскладку приборов AA NAV (`navLayout`); `master` бэкап v1 хранит только настройки (protobuf) и журнал прожигов.
- Цель для `master`: кнопка «Установить из Play-источника» при обновлении по воздуху и бэкап профилей, включая приборы (гауги).

## 1. Кнопка установки с Play-источником (OTA)

### 1.1. Что изменить в `SettingsActivity.kt`

- Убрать зависимость от AAEnabler для OTA-пути: вместо `installDownloaded(downloadId)` (запуск AAEnabler или обычной установки) вызывать `installPlaySource(downloadId)`.
- Добавить флаг `private var pendingPlaySourceInstall: Boolean = false`.
- Добавить приёмник `br` (уже есть) → `verifyAndInstall(downloadId)`.

`verifyAndInstall(downloadId)`:
1. Проверить SHA-256 скачанного APK по `<apk>.sha256` релиза (`expectedApkSha256`), при несовпадении удалить файл и показать `update_corrupt`.
2. Если `pendingPlaySourceInstall == true` — сбросить флаг и сразу `installPlaySource(downloadId)`.
3. Иначе показать диалог «Загрузка завершена» с кнопками `install_play_source` / `cancel`, по OK → `installPlaySource(downloadId)`.

`installPlaySource(downloadId)`:
```kotlin
val uri = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).getUriForDownloadedFile(downloadId) ?: return
val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    Intent(Intent.ACTION_INSTALL_PACKAGE).also {
        it.data = uri
        it.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    }
} else {
    Intent(Intent.ACTION_VIEW).also {
        it.setDataAndTypeAndNormalize(uri, "application/vnd.android.package-archive")
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending")
runCatching { applicationContext.startActivity(intent) }
    .onFailure { Timber.e(it, "Play-source install failed") }
```

`installApk()` (пункт настроек) — диалог с двумя вариантами:
- «Установить из Play-источника» → `installFromPlaySource()`
- «Выбрать файл из памяти» → `installApkFromStorageLauncher.launch(arrayOf("application/vnd.android.package-archive", "*/*"))`

`installFromPlaySource()`:
```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    if (!checkUpdate(installPlaySource = true)) {
        runOnUiThread { Toast.makeText(this@SettingsActivity, R.string.play_source_no_release, Toast.LENGTH_LONG).show() }
    }
}
```

`installPlaySourceUri(uri)` — как `installPlaySource(downloadId)`, но с `uri` из picker (для «Выбрать файл из памяти»).

`checkUpdate(force, installPlaySource)`:
- Для `master` канал стабильный: `stableTag()` (редирект `/releases/latest`), `UpdateCheck.isNewer(tagName, VERSION_CODE)`.
- Если `installPlaySource == true`:
  - получить `apk` через `apkAssetName(tagName, navChannel=false)` (кандидаты `app-release.apk`, `app-stable-release.apk`, `BuildConfig.APK_ASSET`; сначала `assetNameFromReleasePage`);
  - если `apk == null` → return false;
  - `pendingTag`, `pendingApkName`;
  - `pendingPlaySourceInstall = true`;
  - зарегистрировать `br` на `ACTION_DOWNLOAD_COMPLETE`;
  - enqueue DownloadManager с `VISIBILITY_VISIBLE_NOTIFY_COMPLETED`, удалить старый файл;
  - toast `play_source_download_started`;
  - return true.
- Обычный путь (Snackbar «Новая версия» → «Скачать») оставить как есть: после скачивания сработает `verifyAndInstall` и покажет диалог.

`showFirstRunDialog()` — показать один раз в `onCreate` при `savedInstanceState == null` (флаг `app_prefs` → `first_run_seen`), чтобы объяснить установку с Play-источником.

### 1.2. Дополнительно

- `SettingsFragment.kt`: пункт «Установить APK» → `installApk()`.
- Строки (синхронно `values/` и `values-ru/`): `install_apk`, `install_play_source`, `install_from_storage`, `download_complete`, `download_dialog`, `play_source_download_started`, `play_source_no_release`, `first_run_title`, `first_run_message`, `first_run_ok`, `update_corrupt`.
- Manifest: `REQUEST_INSTALL_PACKAGES` не нужен при `ACTION_INSTALL_PACKAGE` с Play-источником; нужны `INTERNET`, `ACCESS_NETWORK_STATE`, `WRITE_EXTERNAL_STORAGE` (для старых API) — проверить текущий manifest.
- `BuildConfig`: `OTA_ENABLED`, `REPO_URL`, `RELEASE_URL`, `APK_ASSET`, `VERSION_CODE`, `VERSION_NAME` уже есть в `master`.

### 1.3. Что убрать

- Не удалять AAEnabler из README сразу, если пользователь захочет оставить как запасной путь; но OTA-кнопка больше не зависит от него. В NAV AAEnabler удалён полностью по просьбе пользователя — в `master` решать отдельно.

## 2. Бэкап профилей, включая приборы

### 2.1. Обновить `BackupCodec.kt` до v2 (совместимо с NAV)

Формат:
```
AA_MONITOR_BACKUP;1
<base64 settings>
<regenLog>

AA_MONITOR_BACKUP;2
<base64 settings>
<base64 layoutJson>
<regenLog>
```

- `data class Backup(val settings: ByteArray, val regenLog: String, val layout: String = "")` (в NAV поле называется `navLayout`, имя поля не влияет на файл).
- `encode(settings, regenLog, layout = "")`: если `layout.isBlank()` → v1, иначе v2.
- `decode`: читать v1 и v2; v2 раскодировать третий блок как UTF-8 JSON.
- Старые v1-бэкапы читаются как раньше.

### 2.2. Экспорт/импорт в `SettingsActivity.kt`

Экспорт:
```kotlin
val data = applicationContext.dataStore.data.first()
val tracker = RegenTracker.get(getSharedPreferences(OBD_PREFS, MODE_PRIVATE))
val layoutJson = "" // TODO: сериализация раскладки приборов master, если появится
val text = BackupCodec.encode(data.toByteArray(), tracker.exportText(), layoutJson)
```

Импорт:
```kotlin
val backup = BackupCodec.decode(text) ?: return@runCatching null
val prefs = UserPreference.parseFrom(backup.settings)
applicationContext.dataStore.updateData { prefs }
RegenTracker.get(getSharedPreferences(OBD_PREFS, MODE_PRIVATE)).importText(backup.regenLog)
backup.layout.takeIf { it.isNotBlank() }?.let { layout ->
    // TODO: NavLayoutStore.parse(layout)?.let { NavLayoutStore.save(...) } для master-аналога
}
```

### 2.3. «В т.ч. приборах»

- В `master` конфигурация гаугов (какой pid на левом/центральном/правом, шкалы, типы) хранится в `UserPreference` (protobuf) и уже попадает в бэкап через `settings`. Поэтому бэкап v1 уже покрывает приборы на уровне настроек.
- Поле `layout` в v2 нужно для будущей/отдельной раскладки (как `navLayout` в NAV). Если в `master` появится редактор раскладки — сериализовать его JSON туда; иначе оставить пустым (тогда файл будет v1, обратная совместимость сохранится).
- Если нужна совместимость с NAV-бэкапами (чтобы импортировать файл из NAV в master) — читать `layout` как опциональный; при неизвестном JSON просто игнорировать.

### 2.4. Тесты

- Обновить `BackupCodecTest.kt`: добавить кейсы v2 (encode/decode с layout, v1 без layout, повреждённый base64 → null).
- Проверка: `:app:testDebugUnitTest` (или `testNavDebugUnitTest` для nav).

## 3. Порядок работ

1. Перенести OTA-кнопку (раздел 1) в `master`, собрать `:app:assembleDebug`, проверить на телефоне: скачивание → диалог → установка с `installerPackageName=com.android.vending`.
2. Обновить `BackupCodec` до v2 (раздел 2.1), обновить экспорт/импорт (2.2), прогнать `BackupCodecTest`.
3. При желании — убрать AAEnabler из README/кода (отдельная задача, как в NAV).

## 4. Ограничения

- `version.properties` не менять без команды; `master`/`beta` трогать только по команде.
- Секреты не печатать; APK подписывается в CI.
- Строки синхронно в `values/` и `values-ru/`.
- `artifacts/` не коммитить.
