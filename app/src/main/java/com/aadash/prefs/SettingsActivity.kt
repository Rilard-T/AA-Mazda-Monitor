package com.aadash.prefs

import android.Manifest
import com.aadash.obd.ObdBridgeService
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.aadash.datastore.UserPreference
import com.aadash.obd.RegenTracker
import com.aadash.stats.App
import com.aadash.prefs.AppAccent
import com.aadash.prefs.userDataStore
import com.aadash.stats.BuildConfig
import com.aadash.stats.CreditsFragment
import com.aadash.stats.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection


class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    /** Тег и имя APK текущей загрузки — для проверки SHA-256 после скачивания. */
    private var pendingTag: String? = null
    private var pendingApkName: String? = null

    /** Загрузка запущена кнопкой «Установить из Play-источника». */
    private var pendingPlaySourceInstall: Boolean = false
    private var brRegistered: Boolean = false

    val br: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.`package` == BuildConfig.APPLICATION_ID) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId != -1L) verifyAndInstall(downloadId)
            }
        }
    }

    /**
     * Сверяет SHA-256 скачанного APK с файлом `<apk>.sha256` из релиза. Если файла нет
     * (старые выпуски), проверка пропускается. При несовпадении файл удаляется.
     */
    private fun verifyAndInstall(downloadId: Long) {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        lifecycleScope.launch(Dispatchers.IO) {
            val expected = expectedApkSha256(pendingTag, pendingApkName)
            val actual = runCatching {
                dm.getUriForDownloadedFile(downloadId)?.let { uri ->
                    contentResolver.openInputStream(uri)?.use { ApkDigest.sha256(it) }
                }
            }.getOrNull()
            val ok = ApkDigest.matches(expected, actual.orEmpty())
            runOnUiThread {
                if (!ok) {
                    runCatching { dm.remove(downloadId) }
                    Toast.makeText(this@SettingsActivity, R.string.update_corrupt, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                if (pendingPlaySourceInstall) {
                    pendingPlaySourceInstall = false
                    installPlaySource(downloadId)
                    return@runOnUiThread
                }
                AlertDialog.Builder(this@SettingsActivity).setTitle(R.string.download_complete)
                    .setMessage(R.string.download_dialog)
                    .setPositiveButton(R.string.install_play_source) { _, _ ->
                        installPlaySource(downloadId)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun expectedApkSha256(tag: String?, apk: String?): String? {
        if (tag.isNullOrBlank() || apk.isNullOrBlank()) return null
        val conn = URL("${BuildConfig.REPO_URL}/releases/download/$tag/$apk.sha256")
            .openConnection() as HttpsURLConnection
        return try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Timber.w("Проверка обновлений: хеш %s.sha256 — код %d", apk, conn.responseCode)
                null
            } else {
                conn.inputStream.bufferedReader().use { ApkDigest.parse(it.readText()) }
            }
        } catch (e: IOException) {
            Timber.w("Проверка обновлений: хеш APK не получен (%s)", e.message)
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Установка скачанного APK с источником com.android.vending. */
    private fun installPlaySource(downloadId: Long) {
        val uri = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager)
            .getUriForDownloadedFile(downloadId) ?: return
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
    }

    /** Выбор: скачать из релиза или выбрать APK из памяти. */
    fun installApk() {
        AlertDialog.Builder(this)
            .setTitle(R.string.install_apk)
            .setItems(
                arrayOf(
                    getString(R.string.install_play_source),
                    getString(R.string.install_from_storage)
                )
            ) { _, which ->
                if (which == 0) {
                    installFromPlaySource()
                } else {
                    installApkFromStorageLauncher.launch(
                        arrayOf(
                            "application/vnd.android.package-archive",
                            "*/*"
                        )
                    )
                }
            }
            .show()
    }

    /** Установка выбранного APK с источником com.android.vending. */
    private fun installPlaySourceUri(uri: Uri) {
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
            .onFailure { Timber.e(it, "Play-source install from storage failed") }
    }

    /** Первый запуск: коротко объясняет, как ставить обновления с Play-источником. */
    private fun showFirstRunDialog() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("first_run_seen", false)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.first_run_title)
            .setMessage(R.string.first_run_message)
            .setPositiveButton(R.string.first_run_ok) { _, _ ->
                prefs.edit().putBoolean("first_run_seen", true).apply()
            }
            .setCancelable(false)
            .show()
    }

    /** Инверсный акцент темы: им подкрашивается текст действия в Snackbar на тёмной плашке. */
    private fun snackbarActionColor(): Int {
        val value = TypedValue()
        return if (theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryInverse, value, true)) {
            value.data
        } else {
            resources.getColor(R.color.snackbarBtnText, theme)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        AppAccent.applyTo(this)
        super.onCreate(savedInstanceState)
        // Служба OBD больше не поднимается при каждом открытии настроек: адаптер занимается
        // только когда пользователь сам заходит на экран «Подключение к OBD» или запускает AA.
        com.aadash.obd.AppExit.installBackConfirm(this)
        setContentView(R.layout.activity_settings)
        supportActionBar!!.setDisplayUseLogoEnabled(true)
        if (savedInstanceState == null) showFirstRunDialog()
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_fragment, SettingsFragment())
                .commit()
        }
        lifecycleScope.launch(Dispatchers.IO) {
            checkUpdate()
        }
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    super.onFragmentResumed(fm, f)
                    supportActionBar!!.subtitle = when (f) {
                        is SettingsDashboard -> {
                            resources.getString(
                                R.string.pref_data_element_settings,
                                f.dashboardIndex() + 1
                            )
                        }

                        is SettingsPIDFragment -> {
                            f.requireArguments().getCharSequence("title")
                        }

                        else -> {
                            null
                        }
                    }
                    supportActionBar!!.setDisplayHomeAsUpEnabled(f !is SettingsFragment)
                }
            }, false
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_copy_logs -> {
                logsToClipboard()
                true
            }

            R.id.action_credits -> {
                launchFragment("credits", CreditsFragment())
                true
            }

            R.id.force_update -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    if (!checkUpdate(true)) {
                        runOnUiThread {
                            Toast.makeText(this@SettingsActivity, R.string.update_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                true
            }

            R.id.install_play_source -> {
                installApk()
                true
            }

            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Один пункт меню: сохранение и восстановление настроек вместе с журналом прожигов. */
    fun backupRestore() {
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_title)
            .setItems(
                arrayOf(getString(R.string.backup_export), getString(R.string.backup_import))
            ) { _, which ->
                if (which == 0) {
                    backupExportLauncher.launch("aa-mazda-backup.txt")
                } else {
                    backupImportLauncher.launch("text/plain")
                }
            }
            .show()
    }

    fun openFile() {
        importFileLauncher.launch(EXPORT_MIME)
    }

    fun exportFile() {
        exportFileLauncher.launch("")
    }

    fun exportRegenLog() {
        regenExportLauncher.launch("regen-log.csv")
    }

    fun importRegenLog() {
        regenImportLauncher.launch("text/plain")
    }

    private fun launchFragment(
        tag: String,
        fragment: Fragment
    ) {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing?.isVisible == true) return
        supportFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(
                R.id.settings_fragment,
                existing ?: fragment,
                tag
            )
            .addToBackStack(null)
            .commit()
    }

    private val backupExportLauncher = registerForActivityResult(TextExportContract()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val data = userDataStore(applicationContext).data.first()
                val tracker = RegenTracker.get(
                    getSharedPreferences(AppProfiles.obdPrefsName(applicationContext), MODE_PRIVATE)
                )
                com.aadash.nav.NavLayoutStore.load(applicationContext)
                val layoutJson = com.aadash.nav.NavLayoutStore.toJson(com.aadash.nav.NavLayoutStore.layout)
                val text = BackupCodec.encode(data.toByteArray(), tracker.exportText(), layoutJson)
                contentResolver.openOutputStream(uri)
                    ?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
            runOnUiThread {
                Toast.makeText(
                    baseContext,
                    if (ok) R.string.backup_exported else R.string.export_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val backupImportLauncher = registerForActivityResult(ImportFileContract()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val count = runCatching {
                val text = contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: return@runCatching null
                val backup = BackupCodec.decode(text) ?: return@runCatching null
                val prefs = UserPreference.parseFrom(backup.settings)
                userDataStore(applicationContext).updateData { prefs }
                RegenTracker.get(
                    getSharedPreferences(AppProfiles.obdPrefsName(applicationContext), MODE_PRIVATE)
                ).importText(backup.regenLog)
                backup.navLayout.takeIf { it.isNotBlank() }?.let { layout ->
                    com.aadash.nav.NavLayoutStore.parse(layout)?.let { parsed ->
                        com.aadash.nav.NavLayoutStore.save(applicationContext, parsed)
                    }
                }
            }.getOrNull()
            runOnUiThread {
                val msg = if (count == null) {
                    getString(R.string.backup_invalid)
                } else {
                    getString(R.string.backup_imported, count)
                }
                Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val installApkFromStorageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        installPlaySourceUri(uri)
    }

    private val regenExportLauncher = registerForActivityResult(TextExportContract()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val tracker = RegenTracker.get(getSharedPreferences(com.aadash.prefs.AppProfiles.obdPrefsName(this@SettingsActivity), MODE_PRIVATE))
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                contentResolver.openOutputStream(uri)?.use { tracker.exportText().toByteArray(Charsets.UTF_8).let(it::write) } != null
            }.getOrDefault(false)
            runOnUiThread {
                Toast.makeText(
                    baseContext,
                    if (ok) R.string.regen_exported else R.string.export_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val regenImportLauncher = registerForActivityResult(ImportFileContract()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val tracker = RegenTracker.get(getSharedPreferences(com.aadash.prefs.AppProfiles.obdPrefsName(this@SettingsActivity), MODE_PRIVATE))
        lifecycleScope.launch(Dispatchers.IO) {
            val count = runCatching {
                contentResolver.openInputStream(uri)
                    ?.use { tracker.importText(it.readBytes().toString(Charsets.UTF_8)) }
            }.getOrNull()
            runOnUiThread {
                val text = when {
                    count == null -> getString(R.string.import_failed)
                    count == 0 -> getString(R.string.regen_import_empty)
                    else -> getString(R.string.regen_imported, count)
                }
                Toast.makeText(baseContext, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val exportFileLauncher = registerForActivityResult(ExportFileContract()) { uri: Uri? ->
        lifecycleScope.launch(Dispatchers.IO) {
            val data = userDataStore(applicationContext).data.first()
            val result = if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    UserPreferenceSerializer.writeTo(data, outputStream)
                }
                R.string.file_exported_successfully
            } else {
                R.string.export_failed
            }
            runOnUiThread {
                Toast.makeText(baseContext, result, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importFileLauncher = registerForActivityResult(ImportFileContract()) { uri: Uri? ->
        if (uri != null) {
            val inStream = contentResolver.openInputStream(uri)
            this@SettingsActivity.lifecycleScope.launch {
                try {
                    userDataStore(this@SettingsActivity.applicationContext).updateData {
                        return@updateData UserPreference.parseFrom(inStream)
                    }
                } catch (e: androidx.datastore.preferences.protobuf.InvalidProtocolBufferException) {
                    Toast.makeText(
                        baseContext,
                        R.string.invalid_settings,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    class TextExportContract : ActivityResultContract<String, Uri?>() {

        override fun createIntent(context: Context, input: String): Intent {
            return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, if (input.isBlank()) "aa-mazda-backup.txt" else input)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == Activity.RESULT_OK) intent?.data else null
        }
    }

    class ImportFileContract : ActivityResultContract<String, Uri?>() {

        override fun createIntent(context: Context, input: String): Intent {
            return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = input
            }
        }

        // This function parses the activity result and returns the URI of the user selected location
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == Activity.RESULT_OK) {
                intent?.data
            } else {
                null
            }
        }
    }

    class ExportFileContract : ActivityResultContract<String, Uri?>() {

        override fun createIntent(context: Context, input: String): Intent {
            return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = EXPORT_MIME
                putExtra(Intent.EXTRA_TITLE, "dashboards.pb")
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == Activity.RESULT_OK) intent?.data else null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.FLAVOR != "nav") {
            val permissionsToRequest: MutableList<String> = ArrayList()
            if (ContextCompat.checkSelfPermission(this, PERMISSION_CAR_VENDOR_EXTENSION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(PERMISSION_CAR_VENDOR_EXTENSION)
            }
            if (permissionsToRequest.isNotEmpty()) {
                requestPermissions(permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS)
                return
            }
        }
    }

    override fun onDestroy() {
        if (brRegistered) {
            runCatching { baseContext.unregisterReceiver(br) }
            brRegistered = false
        }
        super.onDestroy()
    }

    private fun registerDownloadReceiver() {
        if (brRegistered) return
        ContextCompat.registerReceiver(
            baseContext,
            br,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        brRegistered = true
    }


    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = Bundle()
        args.putCharSequence("title", pref.title)
        val prefix = pref.extras.getString("prefix") ?: pref.key
        if (prefix.startsWith("clock_") || prefix.startsWith("display")) {
            val parts = prefix.split("_")
            assert(parts.size == 3)
            val isClock = parts[0] == "clock"
            val screen = parts[1].toInt()
            val index = parts[2].toInt()
            args.putBoolean("isClock", isClock)
            args.putInt("screen", screen)
            args.putInt("index", index)
        }
        args.putString("prefix", prefix)
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        )
        fragment.arguments = args
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(R.id.settings_fragment, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }

    private fun checkUpdate(force: Boolean = false, installPlaySource: Boolean = false): Boolean {
        Timber.i("Checking for update")
        if (!BuildConfig.OTA_ENABLED) return true

        // Каналы обновляются из одного репозитория, но по-разному: stable берёт последний
        // обычный выпуск, nav — предрелиз со своим тегом и своим именем файла.
        val navChannel = BuildConfig.FLAVOR == "nav"
        val tagName = if (navChannel) latestNavTag() else stableTag()
        if (tagName.isNullOrBlank()) return false   // нет связи или в выпуске нет подходящего тега

        // Сравниваем коды версий по той же формуле, что в build.gradle, а не строки:
        // «0.10.0» новее «0.9.1», хотя посимвольно это не так. Для nav важен ещё номер сборки.
        val needsUpdate = if (navChannel) {
            UpdateCheck.isNewerNav(
                tagName,
                BuildConfig.VERSION_CODE,
                UpdateCheck.navBuildOf(BuildConfig.VERSION_NAME)
            )
        } else {
            UpdateCheck.isNewer(tagName, BuildConfig.VERSION_CODE)
        }
        if (needsUpdate == null) Timber.w("Проверка обновлений: тег «%s» не разобран", tagName)
        var downloadItem: String? = null
        if (needsUpdate == true || installPlaySource) {
            val apk = apkAssetName(tagName, navChannel)
            if (apk != null) {
                downloadItem = "${BuildConfig.REPO_URL}/releases/download/$tagName/$apk"
                pendingTag = tagName
                pendingApkName = apk
            }
        }

        if (installPlaySource) {
            if (downloadItem == null) return false
            pendingPlaySourceInstall = true
            val fileName = downloadItem.substringAfterLast('/')
            val downloadRequest = DownloadManager.Request(Uri.parse(downloadItem))
            downloadRequest.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
            downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            runCatching {
                java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                ).delete()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                registerDownloadReceiver()
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(downloadRequest)
                Toast.makeText(
                    baseContext,
                    R.string.play_source_download_started,
                    Toast.LENGTH_SHORT
                ).show()
            }
            return true
        }

        if (needsUpdate == true && downloadItem != null) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.new_version,
                    Snackbar.LENGTH_LONG
                )
                    .setDuration(10000)
                    // Цвет действия в Snackbar берём из темы (инверсный акцент), а не из синей
                    // константы приборной панели: иначе текст остаётся синим при другом акценте.
                    .setActionTextColor(snackbarActionColor())
                    .setAction(R.string.download) {
                        registerDownloadReceiver()
                        // Сохраняем под тем же именем, что в релизе: у каналов они разные.
                        val fileName = downloadItem.substringAfterLast('/')
                        val downloadRequest = DownloadManager.Request(Uri.parse(downloadItem))
                        downloadRequest.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            fileName
                        )
                        downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        runCatching {
                            java.io.File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                fileName
                            ).delete()
                        }
                        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(downloadRequest)
                        // Показываем, откуда качается файл (путь вида /логин/репозиторий/releases/download/…)
                        Toast.makeText(baseContext, Uri.parse(downloadItem).path ?: "", Toast.LENGTH_LONG).show()

                        Toast.makeText(
                            baseContext,
                            R.string.download_instructions,
                            Toast.LENGTH_SHORT
                        ).show()
                    }.show()
            }
        }

        if (needsUpdate != true && force) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(R.string.update_latest, BuildConfig.VERSION_NAME),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        return true
    }

    /**
     * Имя APK в релизе. У nav оно своё (app-nav-release.apk — так называется файл сборки),
     * а у stable исторически два: старые выпуски выложены как app-release.apk, новые
     * ci.yml переименовывает в aa-torque.apk. Берём то, что реально есть в выпуске.
     */
    private fun apkAssetName(tag: String, navChannel: Boolean): String? {
        if (navChannel) return BuildConfig.APK_ASSET
        assetNameFromReleasePage(tag)?.let { return it }
        val candidates = listOf("app-release.apk", "app-stable-release.apk", BuildConfig.APK_ASSET)
        return candidates.firstOrNull { assetExists(tag, it) }
    }

    /** Список файлов выпуска (releases/expanded_assets/<тег>) → имя первого APK. */
    private fun assetNameFromReleasePage(tag: String): String? {
        val conn = URL("${BuildConfig.REPO_URL}/releases/expanded_assets/$tag")
            .openConnection() as HttpsURLConnection
        return try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Timber.w("Проверка обновлений: список файлов %s — код %d", tag, conn.responseCode)
                null
            } else {
                conn.inputStream.bufferedReader().use { UpdateCheck.apkAssetName(it.readText(), tag) }
            }
        } catch (e: IOException) {
            Timber.w("Проверка обновлений: список файлов %s не получен (%s)", tag, e.message)
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Есть ли файл с таким именем в релизе (HEAD без скачивания). */
    private fun assetExists(tag: String, name: String): Boolean {
        val conn = URL("${BuildConfig.REPO_URL}/releases/download/$tag/$name")
            .openConnection() as HttpsURLConnection
        return try {
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: IOException) {
            Timber.w("Проверка обновлений: файл %s не проверен (%s)", name, e.message)
            false
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Тег последнего обычного выпуска: GitHub отвечает редиректом с /releases/latest
     * на /releases/tag/<тег>. Редирект не выполняется — тег читается из Location,
     * чтобы не тратить лимит GitHub API (60 запросов в час без токена).
     */
    private fun stableTag(): String? {
        val conn = URL(BuildConfig.RELEASE_URL).openConnection() as HttpsURLConnection
        conn.instanceFollowRedirects = false
        return try {
            conn.connect()
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM) {
                // Location: https://github.com/.../releases/tag/0.8.6
                conn.getHeaderField("Location")?.substringAfterLast("/tag/")?.substringBefore("?")
            } else {
                Timber.w("Проверка обновлений: GitHub ответил %d", code)
                null
            }
        } catch (e: IOException) {
            Timber.w("Проверка обновлений: ошибка сети (%s)", e.message)
            null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Тег последней сборки канала nav. У nav свой префикс тега (nav-…), поэтому его выпуски
     * берутся из atom-ленты релизов, а не из /releases/latest; лента отдаётся как обычный
     * файл и не расходует лимит GitHub API.
     */
    private fun latestNavTag(): String? {
        val conn = URL(BuildConfig.RELEASES_FEED).openConnection() as HttpsURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            conn.inputStream.bufferedReader().use { UpdateCheck.newestNavTag(it.readText()) }
        } catch (e: IOException) {
            Timber.w("Проверка обновлений: лента недоступна (%s)", e.message)
            null
        } catch (e: RuntimeException) {
            Timber.w("Проверка обновлений: лента не разобрана (%s)", e.message)
            null
        } finally {
            conn.disconnect()
        }
    }


    fun logsToClipboard() {
        val logs = (application as App).logTree.logToString()
        com.aadash.obd.ErrorReport.start(this, logs.map { it.toString() })
    }

    /** Скачивает APK текущего релиза и ставит его с источником com.android.vending. */
    fun installFromPlaySource() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!checkUpdate(installPlaySource = true)) {
                runOnUiThread {
                    Toast.makeText(
                        this@SettingsActivity,
                        R.string.play_source_no_release,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun forceUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!checkUpdate(true)) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, R.string.update_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exitApp() {
        com.aadash.obd.AppExit.confirmAndExit(this)
    }

    fun openCredits() {
        launchFragment("credits", CreditsFragment())
    }


    companion object {
        private const val REQUEST_PERMISSIONS = 0
        private const val PERMISSION_CAR_VENDOR_EXTENSION =
            "com.google.android.gms.permission.CAR_VENDOR_EXTENSION"
        private const val OBD_PREFS = "obd_bridge"
        const val EXPORT_MIME = "application/octet-stream"
    }
}

