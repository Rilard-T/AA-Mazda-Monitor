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
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        title = "Экспериментальные функции"
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
        val about = category("О разделе")
        action(
            about, "Зачем это нужно",
            "Сбор данных о саже в момент включения прожига для подбора порога «DPF %». Нажмите, чтобы прочитать подробнее."
        ) { showAbout() }

        // --- сбор данных ---
        val collect = category("Сбор данных")
        val log = SwitchPreferenceCompat(ctx)
        log.key = "exp_soot_log"
        log.title = "Записывать сажу перед прожигом"
        log.summary = "Работает в фоне, пока запущена служба подключения"
        log.setDefaultValue(true)
        collect.addPreference(log)
        val auto = SwitchPreferenceCompat(ctx)
        auto.key = "exp_auto_threshold"
        auto.title = "Порог «DPF %» по наблюдениям"
        auto.summary = "Использовать среднее по 3 последним прожигам вместо ручного порога"
        auto.setDefaultValue(false)
        collect.addPreference(auto)

        // --- данные ---
        val data = category("Данные")
        statePref = info(data, "Прожиг", "—")
        nowPref = info(data, "Сажа сейчас", "—")
        peakPref = info(data, "Максимум с прошлого прожига", "—")
        lastPref = info(data, "Сажа перед последним прожигом", "—")
        avgPref = info(data, "Среднее по 3 последним прожигам", "—")
        val journal = Preference(ctx)
        journal.title = "Журнал прожигов"
        journal.summary = "—"
        journal.setOnPreferenceClickListener { showJournal(); true }
        data.addPreference(journal)
        journalPref = journal

        // --- действия ---
        val actions = category("Действия")
        action(actions, "Применить как ручной порог", "Записать среднее по последним прожигам в «Порог сажи»") {
            val v = tracker.avgSootPeak(3)
            if (v == null) {
                Toast.makeText(ctx, "Пока нет записей о прожигах", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putFloat("dpf_full_gl", v.toFloat()).apply()
                Toast.makeText(ctx, String.format(Locale.US, "Порог: %.2f г/л", v), Toast.LENGTH_LONG).show()
            }
        }
        action(actions, "Очистить журнал", "Удалить записи о саже перед прожигом") {
            AlertDialog.Builder(requireActivity())
                .setTitle("Очистить журнал?")
                .setMessage("Записи о саже перед прожигом будут удалены.")
                .setPositiveButton(android.R.string.yes) { _, _ -> tracker.clearSootLog(); refresh() }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
        wrapTitles(screen)
        refresh()
    }

    private fun stateText(s: Int): String = when (s) {
        RegenState.RUNNING -> if (tracker.regenActive()) "идёт сейчас" else "ждём счётчик"
        RegenState.COMPLETE -> "завершён"
        RegenState.INTERRUPTED -> "прерван"
        else -> "по счётчику"
    }

    private fun g(v: Double?): String =
        if (v == null) "нет данных" else String.format(Locale.US, "%.2f г/л", v)

    private fun refresh() {
        statePref?.summary = when (val s = tracker.liveStatus()) {
            null -> "нет данных (статус блока недоступен)"
            0 -> "не идёт"
            else -> "идёт (статус блока: $s)"
        }
        nowPref?.summary = g(tracker.currentSoot())
        peakPref?.summary = g(tracker.currentSootPeak())
        lastPref?.summary = g(tracker.lastSootPeak())
        avgPref?.summary = g(tracker.avgSootPeak(3))
        val lines = journalLines()
        journalPref?.summary =
            if (lines.isEmpty()) "Пока нет записей. Они появятся при включении прожига, когда приложение работает."
            else lines.take(4).joinToString("\n") +
                (if (lines.size > 4) "\n… ещё ${lines.size - 4}. Нажмите, чтобы открыть весь журнал." else "")
    }

    private fun journalLines(): List<String> {
        val df = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        return tracker.history().asReversed().map {
            df.format(Date(it.time)) + " — " + String.format(Locale.US, "%.2f г/л", it.soot) +
                (it.km?.let { k -> ", после прошлого ${k.toInt()} км" } ?: "") + ", " + stateText(it.state)
        }
    }

    private fun showJournal() {
        val lines = journalLines()
        AlertDialog.Builder(requireActivity())
            .setTitle("Журнал прожигов")
            .setMessage(if (lines.isEmpty()) "Пока нет записей." else lines.joinToString("\n\n"))
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(requireActivity())
            .setTitle("Зачем это нужно")
            .setMessage(
                "Блок двигателя не отдаёт процент заполнения сажевого фильтра: его считают из накопленной сажи " +
                    "и порога прожига.\n\n" +
                    "Реальный порог на вашем моторе пока неизвестен (по данным других владельцев около 6–7,6 г/л).\n\n" +
                    "Запись делается в момент включения прожига, когда блок сообщает ненулевой статус прожига. " +
                    "В журнал попадает максимум сажи до этого момента. Прожиг заканчивается, когда статус 0 держится " +
                    "10 минут.\n\n" +
                    "Прожиг помечается «завершён» (счётчик вырос или двигатель выключили в пределах 10 минут после " +
                    "окончания) или «прерван» (двигатель выключили во время прожига или счётчик не вырос за 3 минуты). " +
                    "В среднем участвуют все завершённые прожиги.\n\n" +
                    "Если приложение подключилось посреди прожига, запись не ведётся. Данные ориентировочные."
            )
            .setPositiveButton("Закрыть", null)
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
