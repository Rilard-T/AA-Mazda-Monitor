package com.aadash.obd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.aadash.stats.R
import java.util.Locale
import java.util.concurrent.Executors

/**
 * «Проверка датчиков»: открывается при первом запуске приложения (и из экрана «Подключение к OBD»).
 * Показывает все параметры и то, отвечает на них блок или нет — чтобы сразу было видно,
 * какие датчики на конкретной машине доступны.
 */
class ObdCheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_App_Settings)
        com.aadash.prefs.AppAccent.applyTo(this)
        super.onCreate(savedInstanceState)
        title = getString(R.string.obd_check_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, ObdCheckFragment())
                .commit()
        }
        // Сам экран показывается только один раз — после первого успешного подключения; дальше он
        // открывается из «Подключение к OBD».
        getSharedPreferences(com.aadash.prefs.AppProfiles.obdPrefsName(applicationContext), MODE_PRIVATE).edit()
            .putBoolean(KEY_SHOWN, true)
            .remove(KEY_PENDING)
            .apply()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val KEY_SHOWN = "sensor_check_shown"
        private const val KEY_PENDING = "sensor_check_pending"
    }
}

class ObdCheckFragment : PreferenceFragmentCompat() {
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var busy = false
    private var statusPref: Preference? = null
    private var ignitionPref: Preference? = null
    private var disableUnresponsivePref: Preference? = null
    private var enableAllPref: Preference? = null

    private val rows = LinkedHashMap<String, Preference>()
    @Volatile private var lastValues: List<Pair<String, Double?>> = emptyList()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = com.aadash.prefs.AppProfiles.obdPrefsName(requireContext())
        val ctx = requireContext()
        val screen = preferenceManager.createPreferenceScreen(ctx)
        preferenceScreen = screen

        fun category(title: String): PreferenceCategory {
            val c = PreferenceCategory(ctx)
            c.title = title
            screen.addPreference(c)
            return c
        }

        // --- состояние связи ---
        val st = category(getString(R.string.obd_cat_state))
        val status = Preference(ctx)
        status.title = getString(R.string.obd_pref_link_status)
        status.summary = ObdBridgeService.statusText(ctx)
        status.isSelectable = false
        st.addPreference(status)
        statusPref = status
        val ignition = Preference(ctx)
        ignition.title = getString(R.string.obd_check_ignition)
        ignition.summary = getString(R.string.obd_check_no_link)
        ignition.isSelectable = false
        st.addPreference(ignition)
        ignitionPref = ignition
        val hint = Preference(ctx)
        hint.title = getString(R.string.obd_check_hint)
        hint.isSelectable = false
        st.addPreference(hint)
        val connect = Preference(ctx)
        connect.title = getString(R.string.obd_action_connect)
        connect.summary = getString(R.string.obd_action_connect_summary)
        connect.setOnPreferenceClickListener {
            startActivity(Intent(ctx, ObdSettingsActivity::class.java))
            true
        }
        st.addPreference(connect)

        val obdPrefs = ctx.getSharedPreferences(com.aadash.prefs.AppProfiles.obdPrefsName(ctx), Context.MODE_PRIVATE)
        val disableUnresp = Preference(ctx)
        disableUnresp.title = getString(R.string.obd_check_disable_unresponsive)
        disableUnresp.summary = getString(R.string.obd_check_disable_unresponsive_summary)
        disableUnresp.setOnPreferenceClickListener {
            val ign = ObdBridgeService.ignitionOn() == true
            if (ign) {
                val missing = lastValues.filter { it.second == null }.map { it.first }
                ObdEngine.disablePids(obdPrefs, missing)
                refreshDisabledState()
            }
            true
        }
        st.addPreference(disableUnresp)
        disableUnresponsivePref = disableUnresp

        val enableAll = Preference(ctx)
        enableAll.title = getString(R.string.obd_check_enable_all)
        enableAll.setOnPreferenceClickListener {
            ObdEngine.clearDisabledPids(obdPrefs)
            refreshDisabledState()
            true
        }
        st.addPreference(enableAll)
        enableAllPref = enableAll

        // --- датчики по блокам ---
        val engine = category(getString(R.string.obd_check_cat_engine))
        val trans = category(getString(R.string.obd_check_cat_trans))
        val calc = category(getString(R.string.obd_check_cat_calculated))
        for (pid in MazdaPids.all) {
            val cat = when {
                pid.request == null -> calc
                pid.header == "7E1" -> trans
                else -> engine
            }
            val p = Preference(ctx)
            p.title = pid.name(ctx)
            p.summary = getString(R.string.obd_check_waiting)
            p.setOnPreferenceClickListener {
                val nowDisabled = pid.id !in ObdEngine.disabledPids(obdPrefs)
                ObdEngine.setPidDisabled(obdPrefs, pid.id, nowDisabled)
                refreshDisabledState()
                true
            }
            cat.addPreference(p)
            rows[pid.id] = p
        }
        refreshDisabledState()
        wrapTitles(screen)
    }

    private fun refreshDisabledState() {
        val ctx = context ?: return
        val obdPrefs = ctx.getSharedPreferences(com.aadash.prefs.AppProfiles.obdPrefsName(ctx), Context.MODE_PRIVATE)
        val disabled = ObdEngine.disabledPids(obdPrefs)
        enableAllPref?.isVisible = disabled.isNotEmpty()
        enableAllPref?.summary = getString(R.string.obd_check_enable_all_summary, disabled.size)
        for ((id, pref) in rows) {
            if (id in disabled) {
                pref.summary = getString(R.string.obd_check_disabled)
            } else if (pref.summary == getString(R.string.obd_check_disabled)) {
                pref.summary = getString(R.string.obd_check_waiting)
            }
        }
    }

    private fun answer(pid: PidDef, v: Double): String {
        val num = if (Math.abs(v - Math.round(v).toDouble()) < 0.05)
            String.format(Locale.US, "%.0f", v)
        else String.format(Locale.US, "%.2f", v)
        val unit = pid.unit(requireContext()).let { if (it.isEmpty()) "" else " " + it }
        return getString(R.string.obd_check_answer, num + unit)
    }

    private val tick = object : Runnable {
        override fun run() {
            val ctx = context
            if (ctx != null) {
                statusPref?.summary = ObdBridgeService.statusText(ctx)
                ignitionPref?.summary = when (val ign = ObdBridgeService.ignitionOn()) {
                    null -> getString(R.string.obd_check_no_link)
                    true -> getString(R.string.obd_check_ignition_on)
                    false -> getString(R.string.obd_check_ignition_off)
                }
                if (!busy) {
                    busy = true
                    io.execute {
                        val values = MazdaPids.all.map { it.id to ObdBridgeService.peek(it.id) }
                        lastValues = values
                        ui.post {
                            if (isAdded) {
                                // «Нет ответа» показываем только при включённом зажигании, иначе это
                                // просто молчащий блок и сообщение сбивает с толку.
                                val ign = ObdBridgeService.ignitionOn() == true
                                val obdPrefs = ctx.getSharedPreferences(com.aadash.prefs.AppProfiles.obdPrefsName(ctx), Context.MODE_PRIVATE)
                                val disabled = ObdEngine.disabledPids(obdPrefs)
                                enableAllPref?.isVisible = disabled.isNotEmpty()
                                enableAllPref?.summary = getString(R.string.obd_check_enable_all_summary, disabled.size)
                                values.forEach { (id, v) ->
                                    val pid = MazdaPids.byId(id) ?: return@forEach
                                    rows[id]?.summary = when {
                                        id in disabled -> getString(R.string.obd_check_disabled)
                                        v != null -> answer(pid, v)
                                        ign -> getString(R.string.obd_check_no_answer)
                                        else -> getString(R.string.obd_check_waiting)
                                    }
                                }
                            }
                            busy = false   // снимаем флаг всегда, иначе опрос остановится
                        }
                    }
                }
            }
            ui.postDelayed(this, 2000)
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
