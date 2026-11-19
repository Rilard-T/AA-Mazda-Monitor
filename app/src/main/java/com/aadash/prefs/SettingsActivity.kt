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
import com.aadash.stats.BuildConfig
import com.aadash.stats.CreditsFragment
import com.aadash.stats.R
import com.google.android.material.snackbar.Snackbar
import com.google.protobuf.InvalidProtocolBufferException
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

    val br: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.`package` == BuildConfig.APPLICATION_ID) {
                AlertDialog.Builder(this@SettingsActivity).setTitle(R.string.download_complete)
                    .setMessage(R.string.download_dialog)
                    .setPositiveButton(android.R.string.ok, object: DialogInterface.OnClickListener{
                        override fun onClick(p0: DialogInterface?, p1: Int) {
                            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                            if (downloadId == -1L) return
                            val uri = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).getUriForDownloadedFile(downloadId)
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Intent(Intent.ACTION_INSTALL_PACKAGE).also {
                                    it.data = uri
                                    it.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                            } else {
                                Intent(Intent.ACTION_VIEW).also {
                                    it.setDataAndTypeAndNormalize(
                                        uri,
                                        "application/vnd.android.package-archive"
                                    )
                                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, "com.android.vending")
                            applicationContext.startActivity(intent)
                        }
                    }).setNegativeButton(android.R.string.cancel
                    ) { p0, p1 -> p0?.dismiss() }.show()
            }
        }
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

            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    fun openFile() {
        importFileLauncher.launch(EXPORT_MIME)
    }

    fun exportFile() {
        exportFileLauncher.launch("")
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

    private val exportFileLauncher = registerForActivityResult(ExportFileContract()) { uri: Uri? ->
        // This lambda will be executed when the activity result returns
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

    private val importFileLauncher = registerForActivityResult(ImportFileContract()) { uri: Uri? ->
        if (uri != null) {
            val inStream = contentResolver.openInputStream(uri)
            this@SettingsActivity.lifecycleScope.launch {
                try {
                    userDataStore(this@SettingsActivity.applicationContext).updateData {
                        return@updateData UserPreference.parseFrom(inStream)
                    }
                } catch (e: InvalidProtocolBufferException) {
                    Toast.makeText(
                        baseContext,
                        R.string.invalid_settings,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    class ExportFileContract : ActivityResultContract<String, Uri?>() {

        override fun createIntent(context: Context, input: String): Intent {
            return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = EXPORT_MIME
                // Suggest a file name based on the original file name
                putExtra(Intent.EXTRA_TITLE, "dashboards.pb")
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

    class TextExportContract : ActivityResultContract<String, Uri?>() {

        override fun createIntent(context: Context, input: String): Intent {
            return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "regen-log.csv")
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
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

    private fun checkUpdate(force: Boolean =false): Boolean {
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
        val downloadItem: String? = if (needsUpdate == true) {
            "${BuildConfig.REPO_URL}/releases/download/$tagName/${BuildConfig.APK_ASSET}"
        } else null

        if (needsUpdate == true && downloadItem != null) {
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
                    ContextCompat.registerReceiver(
                        baseContext,
                        br,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        ContextCompat.RECEIVER_EXPORTED
                    )
                    val downloadRequest = DownloadManager.Request(Uri.parse(downloadItem))
                    downloadRequest.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        BuildConfig.APK_ASSET
                    )
                    downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    runCatching {
                        java.io.File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            BuildConfig.APK_ASSET
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
     * Тег последней сборки канала nav. Все выпуски nav помечены как предрелизы, поэтому
     * /releases/latest их не видит, а страница /releases не отдаёт редирект на тег.
     * Atom-лента релизов отдаётся как обычный файл и не расходует лимит GitHub API.
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

    fun exportRegenLog() {
        regenExportLauncher.launch("")
    }

    fun importRegenLog() {
        regenImportLauncher.launch("text/plain")
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
        const val EXPORT_MIME = "application/octet-stream"
    }
}

