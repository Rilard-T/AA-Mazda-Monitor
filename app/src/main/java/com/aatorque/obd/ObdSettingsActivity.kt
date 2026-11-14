package com.aatorque.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.aatorque.stats.R
import java.util.Locale
import java.util.concurrent.Executors

/** Экран «Подключение к OBD» в стиле остальных настроек приложения (тема AppTheme, список настроек). */
class ObdSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_App_Settings)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        title = getString(R.string.obd_connection_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, ObdPrefsFragment())
                .commit()
        }
        askPermissionsThenStart()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    fun askPermissionsThenStart() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isEmpty()) ObdBridgeService.start(this)
        else requestPermissions(need.toTypedArray(), 77)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) {
            ObdBridgeService.start(this)
            (supportFragmentManager.findFragmentById(android.R.id.content) as? ObdPrefsFragment)?.refreshAdapters()
        }
        if (requestCode == 78 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            (supportFragmentManager.findFragmentById(android.R.id.content) as? ObdPrefsFragment)?.startBleScan()
        }
    }

    /** Для поиска BLE: BLUETOOTH_SCAN (Android 12+) или геолокация (Android 11 и ниже). */
    fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= 31)
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        else
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun requestScanPermission() {
        val p = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN
        else Manifest.permission.ACCESS_FINE_LOCATION
        requestPermissions(arrayOf(p), 78)
    }
}

class ObdPrefsFragment : PreferenceFragmentCompat() {
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var statusPref: Preference? = null
    private var valuesPref: Preference? = null
    private var adapterPref: ListPreference? = null
    private val classicPrefs = mutableListOf<Preference>()
    private val blePrefs = mutableListOf<Preference>()
    private val wifiPrefs = mutableListOf<Preference>()
    private var bleScanPref: Preference? = null
    private var blePref: ListPreference? = null
    private val scanner = BleScanner()
    @Volatile private var busy = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "obd_bridge"
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("obd_bridge", Context.MODE_PRIVATE)
        val screen = preferenceManager.createPreferenceScreen(ctx)
        preferenceScreen = screen

        fun category(title: String): PreferenceCategory {
            val c = PreferenceCategory(ctx)
            c.title = title
            screen.addPreference(c)
            return c
        }
        fun action(cat: PreferenceCategory, title: String, summary: String?, onClick: () -> Unit) {
            val p = Preference(ctx)
            p.title = title
            if (summary != null) p.summary = summary
            p.setOnPreferenceClickListener { onClick(); true }
            cat.addPreference(p)
        }

        // --- состояние ---
        val st = category(getString(R.string.obd_cat_state))
        val status = Preference(ctx)
        status.title = getString(R.string.obd_pref_link_status)
        status.summary = ObdBridgeService.statusText(ctx)
        status.isSelectable = false
        st.addPreference(status)
        statusPref = status
        val values = Preference(ctx)
        values.title = getString(R.string.obd_pref_values)
        values.summary = "—"
        values.isSelectable = false
        st.addPreference(values)
        valuesPref = values

        // --- подключение ---
        val con = category(getString(R.string.obd_cat_connection))
        action(
            con,
            getString(R.string.obd_action_connect),
            getString(R.string.obd_action_connect_summary)
        ) {
            (activity as? ObdSettingsActivity)?.askPermissionsThenStart()
            ObdBridgeService.reconnect(ctx)
        }
        action(con, getString(R.string.obd_action_stop), getString(R.string.obd_action_stop_summary)) {
            ObdBridgeService.stop(ctx)
        }

        val type = ListPreference(ctx)
        type.key = "link_type"
        type.title = getString(R.string.obd_pref_link_type)
        type.dialogTitle = getString(R.string.obd_pref_link_type_dialog)
        type.entries = arrayOf(
            getString(R.string.obd_link_classic),
            getString(R.string.obd_link_ble),
            getString(R.string.obd_link_wifi)
        )
        type.entryValues = arrayOf("classic", "ble", "wifi")
        type.setDefaultValue("classic")
        type.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        type.setOnPreferenceChangeListener { _, v ->
            updateLinkPrefs(v.toString())
            ObdBridgeService.reconnect(ctx)
            true
        }
        con.addPreference(type)

        // Bluetooth Classic
        val btSettings = Preference(ctx)
        btSettings.title = getString(R.string.obd_pref_bt_settings)
        btSettings.summary = getString(R.string.obd_pref_bt_settings_summary)
        btSettings.setOnPreferenceClickListener { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); true }
        con.addPreference(btSettings)
        classicPrefs += btSettings
        val ad = ListPreference(ctx)
        ad.key = "obd_mac"
        ad.title = getString(R.string.obd_pref_adapter)
        ad.dialogTitle = getString(R.string.obd_pref_adapter_dialog)
        ad.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        ad.setOnPreferenceChangeListener { _, _ -> ObdBridgeService.reconnect(ctx); true }
        con.addPreference(ad)
        adapterPref = ad
        classicPrefs += ad
        refreshAdapters()

        // Bluetooth LE
        val bleScan = Preference(ctx)
        bleScan.title = getString(R.string.obd_pref_find_ble)
        bleScan.summary = getString(R.string.obd_pref_find_ble_summary)
        bleScan.setOnPreferenceClickListener { startBleScan(); true }
        con.addPreference(bleScan)
        blePrefs += bleScan
        bleScanPref = bleScan
        val bleMac = ListPreference(ctx)
        bleMac.key = "ble_mac"
        bleMac.title = getString(R.string.obd_pref_ble_adapter)
        bleMac.dialogTitle = getString(R.string.obd_pref_ble_adapter_dialog)
        bleMac.summaryProvider = Preference.SummaryProvider<ListPreference> { p ->
            p.entry?.toString() ?: getString(R.string.obd_ble_none_selected)
        }
        bleMac.setOnPreferenceChangeListener { _, _ -> ObdBridgeService.reconnect(ctx); true }
        con.addPreference(bleMac)
        blePrefs += bleMac
        blePref = bleMac
        fillBle(emptyList())

        // Wi-Fi
        val wifiSettings = Preference(ctx)
        wifiSettings.title = getString(R.string.obd_pref_wifi_settings)
        wifiSettings.summary = getString(R.string.obd_pref_wifi_settings_summary)
        wifiSettings.setOnPreferenceClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); true }
        con.addPreference(wifiSettings)
        wifiPrefs += wifiSettings
        val wifiHost = EditTextPreference(ctx)
        wifiHost.key = "wifi_host"
        wifiHost.title = getString(R.string.obd_pref_wifi_host)
        wifiHost.dialogTitle = getString(R.string.obd_pref_wifi_host_dialog)
        wifiHost.setDefaultValue("192.168.0.10")
        wifiHost.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        wifiHost.setOnPreferenceChangeListener { _, _ -> ObdBridgeService.reconnect(ctx); true }
        con.addPreference(wifiHost)
        wifiPrefs += wifiHost
        val wifiPort = EditTextPreference(ctx)
        wifiPort.key = "wifi_port"
        wifiPort.title = getString(R.string.obd_pref_wifi_port)
        wifiPort.dialogTitle = getString(R.string.obd_pref_wifi_port_dialog)
        wifiPort.setDefaultValue("35000")
        wifiPort.setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER }
        wifiPort.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        wifiPort.setOnPreferenceChangeListener { _, _ -> ObdBridgeService.reconnect(ctx); true }
        con.addPreference(wifiPort)
        wifiPrefs += wifiPort

        updateLinkPrefs(prefs.getString("link_type", "classic") ?: "classic")

        // --- опрос датчиков ---
        val poll = category(getString(R.string.obd_cat_polling))
        val fast = ListPreference(ctx)
        fast.key = "poll_fast_ms"
        fast.title = getString(R.string.obd_pref_fast)
        fast.dialogTitle = getString(R.string.obd_pref_fast_dialog)
        fast.entries = arrayOf(
            getString(R.string.obd_fast_max),
            getString(R.string.obd_fast_high),
            getString(R.string.obd_fast_normal),
            getString(R.string.obd_fast_economy)
        )
        fast.entryValues = arrayOf("0", "100", "200", "500")
        fast.setDefaultValue("100")
        fast.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        poll.addPreference(fast)
        val slow = ListPreference(ctx)
        slow.key = "poll_slow_ms"
        slow.title = getString(R.string.obd_pref_slow)
        slow.dialogTitle = getString(R.string.obd_pref_slow_dialog)
        slow.entries = arrayOf(
            getString(R.string.obd_slow_frequent),
            getString(R.string.obd_slow_normal),
            getString(R.string.obd_slow_rare),
            getString(R.string.obd_slow_very_rare)
        )
        slow.entryValues = arrayOf("1000", "3000", "10000", "30000")
        slow.setDefaultValue("3000")
        slow.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        poll.addPreference(slow)
        val turbo = SwitchPreferenceCompat(ctx)
        turbo.key = "fast_adapter"
        turbo.title = getString(R.string.obd_pref_adapter_accel)
        turbo.summary = getString(R.string.obd_pref_adapter_accel_summary)
        turbo.setDefaultValue(true)
        turbo.setOnPreferenceChangeListener { _, _ -> ObdBridgeService.reconnect(ctx); true }
        poll.addPreference(turbo)

        // --- сажевый фильтр ---
        val dpfCat = category(getString(R.string.obd_cat_dpf))
        val dpf = EditTextPreference(ctx)
        dpf.key = "dpf_full_gl_ui"
        dpf.isPersistent = false                 // число храним сами как float (его читает ObdEngine)
        dpf.title = getString(R.string.obd_pref_dpf_threshold)
        dpf.dialogTitle = getString(
            R.string.obd_pref_dpf_threshold_dialog, ObdConfig.DPF_FULL_GL.toString()
        )
        dpf.text = prefs.getFloat("dpf_full_gl", 0f).takeIf { it > 0 }?.toString() ?: ""
        dpf.setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        dpf.summaryProvider = Preference.SummaryProvider<EditTextPreference> { p ->
            val v = if (p.text.isNullOrBlank())
                getString(R.string.obd_pref_dpf_threshold_default, ObdConfig.DPF_FULL_GL.toString())
            else getString(R.string.obd_pref_dpf_threshold_value, p.text)
            getString(R.string.obd_pref_dpf_threshold_summary, v)
        }
        dpf.setOnPreferenceChangeListener { _, v ->
            val f = v.toString().replace(',', '.').toFloatOrNull() ?: 0f
            prefs.edit().putFloat("dpf_full_gl", f).apply()
            true
        }
        dpfCat.addPreference(dpf)

        // --- приложение ---
        val app = category(getString(R.string.obd_cat_app))
        action(
            app,
            getString(R.string.obd_action_experimental),
            getString(R.string.obd_action_experimental_summary)
        ) {
            startActivity(Intent(ctx, ObdExperimentalActivity::class.java))
        }
        action(app, getString(R.string.obd_action_exit), getString(R.string.obd_action_exit_summary)) {
            activity?.let { AppExit.confirmAndExit(it) }
        }
        wrapTitles(screen)
    }

    /** Показывает настройки только выбранного типа подключения. */
    private fun updateLinkPrefs(type: String) {
        classicPrefs.forEach { it.isVisible = type == "classic" }
        blePrefs.forEach { it.isVisible = type == "ble" }
        wifiPrefs.forEach { it.isVisible = type == "wifi" }
    }

    fun startBleScan() {
        val act = activity as? ObdSettingsActivity ?: return
        if (!act.hasScanPermission()) {
            act.requestScanPermission()
            return
        }
        bleScanPref?.summary = getString(R.string.obd_ble_searching)
        scanner.start(
            10_000,
            onUpdate = { list ->
                fillBle(list)
                bleScanPref?.summary = getString(R.string.obd_ble_found_count, list.size)
            },
            onDone = { list ->
                fillBle(list)
                bleScanPref?.summary =
                    if (list.isEmpty()) getString(R.string.obd_ble_not_found)
                    else getString(R.string.obd_ble_found_hint, list.size)
            }
        )
    }

    private fun fillBle(list: List<BleScanner.Found>) {
        val pref = blePref ?: return
        val ctx = context ?: return
        val saved = ctx.getSharedPreferences("obd_bridge", Context.MODE_PRIVATE)
            .getString("ble_mac", null)?.takeIf { it.isNotBlank() }
        val names = mutableListOf(getString(R.string.obd_ble_none_selected))
        val vals = mutableListOf("")
        list.forEach {
            names += "${it.name}  (${it.address}), ${it.rssi} ${ctx.getString(R.string.obd_ble_rssi_unit)}"
            vals += it.address
        }
        if (saved != null && vals.none { it.equals(saved, ignoreCase = true) }) {
            names += getString(R.string.obd_ble_saved, saved)
            vals += saved
        }
        pref.entries = names.toTypedArray()
        pref.entryValues = vals.toTypedArray()
    }

    @SuppressLint("MissingPermission")
    fun refreshAdapters() {
        val ad = adapterPref ?: return
        val ctx = context ?: return
        val names = mutableListOf(getString(R.string.obd_adapter_auto))
        val vals = mutableListOf("")
        val allowed = Build.VERSION.SDK_INT < 31 ||
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices
                ?.sortedBy { it.name ?: "" }
                ?.forEach {
                    names += "${it.name ?: "?"}  (${it.address})"
                    vals += it.address
                }
        }
        ad.entries = names.toTypedArray()
        ad.entryValues = vals.toTypedArray()
        if (ad.value == null) ad.value = ""
    }

    override fun onResume() {
        super.onResume()
        refreshAdapters()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        scanner.stop()
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private val tick = object : Runnable {
        override fun run() {
            val ctx = context
            if (ctx != null) statusPref?.summary = ObdBridgeService.statusText(ctx)
            if (!busy) {
                busy = true
                io.execute {
                    val text = KEY.joinToString("\n") { (id, nameRes) ->
                        val v = ObdBridgeService.peek(id)
                        ctx?.getString(nameRes) + ": " + (v?.let { String.format(Locale.US, "%.2f", it) } ?: "—")
                    }
                    ui.post { valuesPref?.summary = text; busy = false }
                }
            }
            ui.postDelayed(this, 2000)
        }
    }

    private companion object {
        val KEY = listOf(
            "05" to R.string.obd_val_coolant,
            "0c" to R.string.obd_val_rpm,
            "0d" to R.string.obd_val_speed,
            "ff9005" to R.string.obd_val_atf,
            "ff9006" to R.string.obd_val_regen_status,
            "ff9003" to R.string.obd_val_soot,
            "ff9001" to R.string.obd_val_regen_counter,
            "ff9002" to R.string.obd_val_distance,
        )
    }
}
