package com.aatorque.obd

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.aatorque.stats.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** «Экспериментальные функции»: сбор данных о саже перед прожигом, пока не выяснен реальный порог. */
class ObdExperimentalActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_App_Settings)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        title = getString(R.string.obd_exp_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, ObdExperimentalFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class ObdExperimentalFragment : PreferenceFragmentCompat() {
    private val ui = Handler(Looper.getMainLooper())
    private var statePref: Preference? = null
    private var nowPref: Preference? = null
    private var peakPref: Preference? = null
    private var lastPref: Preference? = null
    private var avgPref: Preference? = null
    private var journalPref: Preference? = null
    private lateinit var tracker: RegenTracker

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "obd_bridge"
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("obd_bridge", Context.MODE_PRIVATE)
        tracker = RegenTracker.get(prefs)
        val screen = preferenceManager.createPreferenceScreen(ctx)
        preferenceScreen = screen

        fun category(title: String): PreferenceCategory {
            val c = PreferenceCategory(ctx)
            c.title = title
            screen.addPreference(c)
            return c
        }
        fun info(cat: PreferenceCategory, title: String, summary: String): Preference {
            val p = Preference(ctx)
            p.title = title
            p.summary = summary
            p.isSelectable = false
            cat.addPreference(p)
            return p
        }
        fun action(cat: PreferenceCategory, title: String, summary: String, onClick: () -> Unit) {
            val p = Preference(ctx)
            p.title = title
            p.summary = summary
            p.setOnPreferenceClickListener { onClick(); true }
            cat.addPreference(p)
        }

        // --- описание ---
        val about = category(getString(R.string.obd_exp_cat_about))
        action(
            about,
            getString(R.string.obd_exp_about_action),
            getString(R.string.obd_exp_about_action_summary)
        ) { showAbout() }

        // --- сбор данных ---
        val collect = category(getString(R.string.obd_exp_cat_collect))
        val log = SwitchPreferenceCompat(ctx)
        log.key = "exp_soot_log"
        log.title = getString(R.string.obd_exp_log_soot)
        log.summary = getString(R.string.obd_exp_log_soot_summary)
        log.setDefaultValue(true)
        collect.addPreference(log)
        val auto = SwitchPreferenceCompat(ctx)
        auto.key = "exp_auto_threshold"
        auto.title = getString(R.string.obd_exp_auto_threshold)
        auto.summary = getString(R.string.obd_exp_auto_threshold_summary)
        auto.setDefaultValue(false)
        collect.addPreference(auto)

        // --- данные ---
        val data = category(getString(R.string.obd_exp_cat_data))
        statePref = info(data, getString(R.string.obd_exp_data_regen), "—")
        nowPref = info(data, getString(R.string.obd_exp_data_soot_now), "—")
        peakPref = info(data, getString(R.string.obd_exp_data_peak), "—")
        lastPref = info(data, getString(R.string.obd_exp_data_last), "—")
        avgPref = info(data, getString(R.string.obd_exp_data_avg), "—")
        val journal = Preference(ctx)
        journal.title = getString(R.string.obd_exp_journal)
        journal.summary = "—"
        journal.setOnPreferenceClickListener { showJournal(); true }
        data.addPreference(journal)
        journalPref = journal

        // --- действия ---
        val actions = category(getString(R.string.obd_exp_cat_actions))
        action(
            actions,
            getString(R.string.obd_exp_apply_threshold),
            getString(R.string.obd_exp_apply_threshold_summary)
        ) {
            val v = tracker.avgSootPeak(3)
            if (v == null) {
                Toast.makeText(ctx, getString(R.string.obd_exp_no_regenerations), Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putFloat("dpf_full_gl", v.toFloat()).apply()
                Toast.makeText(
                    ctx,
                    getString(R.string.obd_exp_threshold_applied, String.format(Locale.US, "%.2f", v)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        action(
            actions,
            getString(R.string.obd_exp_clear),
            getString(R.string.obd_exp_clear_summary)
        ) {
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.obd_exp_clear_title)
                .setMessage(R.string.obd_exp_clear_message)
                .setPositiveButton(android.R.string.yes) { _, _ -> tracker.clearSootLog(); refresh() }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
        wrapTitles(screen)
        refresh()
    }

    private fun stateText(s: Int): String = when (s) {
        RegenState.RUNNING -> if (tracker.regenActive()) getString(R.string.obd_exp_state_running)
        else getString(R.string.obd_exp_state_waiting)
        RegenState.COMPLETE -> getString(R.string.obd_exp_state_complete)
        RegenState.INTERRUPTED -> getString(R.string.obd_exp_state_interrupted)
        else -> getString(R.string.obd_exp_state_by_counter)
    }

    private fun g(v: Double?): String =
        if (v == null) getString(R.string.obd_exp_no_data)
        else getString(R.string.obd_exp_value_gl, String.format(Locale.US, "%.2f", v))

    private fun refresh() {
        statePref?.summary = when (val s = tracker.liveStatus()) {
            null -> getString(R.string.obd_exp_status_no_data)
            0 -> getString(R.string.obd_exp_status_none)
            else -> getString(R.string.obd_exp_status_running, s)
        }
        nowPref?.summary = g(tracker.currentSoot())
        peakPref?.summary = g(tracker.currentSootPeak())
        lastPref?.summary = g(tracker.lastSootPeak())
        avgPref?.summary = g(tracker.avgSootPeak(3))
        val lines = journalLines()
        journalPref?.summary =
            if (lines.isEmpty()) getString(R.string.obd_exp_journal_empty)
            else lines.take(4).joinToString("\n") +
                (if (lines.size > 4) getString(R.string.obd_exp_journal_more, lines.size - 4) else "")
    }

    private fun journalLines(): List<String> {
        val df = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        return tracker.history().asReversed().map {
            val date = df.format(Date(it.time))
            val soot = String.format(Locale.US, "%.2f", it.soot)
            val state = stateText(it.state)
            it.km?.let { k -> getString(R.string.obd_exp_journal_line_km, date, soot, k.toInt(), state) }
                ?: getString(R.string.obd_exp_journal_line, date, soot, state)
        }
    }

    private fun showJournal() {
        val lines = journalLines()
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.obd_exp_journal)
            .setMessage(if (lines.isEmpty()) getString(R.string.obd_exp_journal_none) else lines.joinToString("\n\n"))
            .setPositiveButton(R.string.obd_exp_close, null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.obd_exp_about_title)
            .setMessage(R.string.obd_exp_about_message)
            .setPositiveButton(R.string.obd_exp_close, null)
            .show()
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
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
}

/** Разрешает длинным заголовкам настроек переноситься на вторую строку вместо «…». */
internal fun wrapTitles(g: PreferenceGroup) {
    for (i in 0 until g.preferenceCount) {
        val p = g.getPreference(i)
        p.isSingleLineTitle = false
        if (p is PreferenceGroup) wrapTitles(p)
    }
}
