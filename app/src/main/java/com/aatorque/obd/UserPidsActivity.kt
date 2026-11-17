package com.aatorque.obd

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.aatorque.stats.R
import java.util.Locale
import java.util.concurrent.Executors

/**
 * «Свои датчики»: отдельный экран для пользовательских PID. Здесь их добавляют вручную
 * или импортом Torque CSV, проверяют вживую и правят. Сохранённый датчик сразу попадает
 * в общий каталог [MazdaPids.all]: он виден в проверке датчиков, в списке PID дашборда
 * и в редакторе раскладки AA NAV.
 */
class UserPidsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_App_Settings)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        title = getString(R.string.obd_user_pids_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, UserPidsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class UserPidsFragment : PreferenceFragmentCompat() {
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var statusPref: Preference? = null
    private var listCat: PreferenceCategory? = null
    private val rows = LinkedHashMap<String, Preference>()
    @Volatile private var busy = false

    /** Выбор CSV-файла: формат Torque, разделитель — запятая или точка с запятой. */
    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importCsv(uri)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()
        val screen = preferenceManager.createPreferenceScreen(ctx)
        preferenceScreen = screen

        fun category(title: String): PreferenceCategory {
            val c = PreferenceCategory(ctx)
            c.title = title
            screen.addPreference(c)
            return c
        }

        // --- состояние ---
        val st = category(getString(R.string.obd_cat_state))
        val status = Preference(ctx)
        status.title = getString(R.string.obd_user_pids_state)
        status.summary = getString(R.string.obd_user_pids_state_summary)
        status.isSelectable = false
        st.addPreference(status)
        statusPref = status

        // --- действия ---
        val act = category(getString(R.string.obd_user_pids_cat_actions))
        val add = Preference(ctx)
        add.title = getString(R.string.obd_user_pid_add)
        add.summary = getString(R.string.obd_user_pid_add_summary)
        add.setOnPreferenceClickListener { editDialog(null); true }
        act.addPreference(add)
        val import = Preference(ctx)
        import.title = getString(R.string.obd_user_pids_import)
        import.summary = getString(R.string.obd_user_pids_import_summary)
        import.setOnPreferenceClickListener { pickCsvFile(); true }
        act.addPreference(import)
        val help = Preference(ctx)
        help.title = getString(R.string.obd_user_pids_help)
        help.summary = getString(R.string.obd_user_pids_help_summary)
        help.setOnPreferenceClickListener { showHelp(); true }
        act.addPreference(help)

        // --- добавленные ---
        listCat = category(getString(R.string.obd_user_pids_cat_list))
        wrapTitles(screen)
        rebuild()
    }

    /** Пересобирает список: после импорта, правки и удаления. */
    private fun rebuild() {
        val ctx = context ?: return
        val cat = listCat ?: return
        cat.removeAll()
        rows.clear()
        val list = UserPids.list()
        if (list.isEmpty()) {
            val empty = Preference(ctx)
            empty.title = getString(R.string.obd_user_pids_empty)
            empty.isSelectable = false
            cat.addPreference(empty)
            return
        }
        for (pid in list) {
            val p = Preference(ctx)
            p.title = pid.name
            p.summary = rowText(ctx, pid, null)
            p.setOnPreferenceClickListener { openActions(pid); true }
            cat.addPreference(p)
            rows[pid.key] = p
        }
    }

    /** Строка датчика: значение, если блок ответил, иначе запрос и формула. */
    private fun rowText(ctx: Context, pid: UserPid, value: Double?): String {
        val unit = pid.unit.takeIf { it.isNotBlank() }?.let { " " + it } ?: ""
        return if (value != null) {
            ctx.getString(R.string.obd_user_pid_row_value, pid.modeAndPid, format(value) + unit)
        } else {
            ctx.getString(R.string.obd_user_pid_row, pid.modeAndPid, pid.equation)
        }
    }

    private fun format(v: Double): String =
        if (Math.abs(v - Math.round(v).toDouble()) < 0.05) String.format(Locale.US, "%.0f", v)
        else String.format(Locale.US, "%.2f", v)

    private val tick = object : Runnable {
        override fun run() {
            val ctx = context
            if (ctx != null && !busy && rows.isNotEmpty()) {
                busy = true
                io.execute {
                    val values = UserPids.list().associate { it.key to ObdBridgeService.peek(it.key) }
                    ui.post {
                        values.forEach { (key, v) ->
                            val pid = UserPids.byKey(key) ?: return@forEach
                            // нет значения — показываем запрос и формулу, а не прошлое число
                            rows[key]?.summary = rowText(ctx, pid, v)
                        }
                        statusPref?.summary = getString(
                            R.string.obd_user_pids_state_count,
                            UserPids.list().size,
                            ObdBridgeService.statusText(ctx),
                        )
                        busy = false
                    }
                }
            }
            ui.postDelayed(this, 2000)
        }
    }

    override fun onResume() {
        super.onResume()
        rebuild()
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

    // ---------------- проверка и правка существующего ----------------

    private fun openActions(pid: UserPid) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(pid.name)
            .setItems(
                arrayOf(
                    getString(R.string.obd_user_pid_test_once),
                    getString(R.string.obd_user_pid_test),
                    getString(R.string.obd_user_pid_edit),
                    getString(R.string.obd_user_pid_delete),
                )
            ) { _, which ->
                when (which) {
                    0 -> availabilityTest(pid)
                    1 -> liveTest(pid)
                    2 -> editDialog(pid)
                    else -> confirmDelete(pid)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Живая проверка: пока диалог открыт, раз в секунду показываем значение от блока. */
    private fun liveTest(pid: UserPid) {
        val ctx = context ?: return
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.obd_user_pid_test_title, pid.name))
            .setMessage(getString(R.string.obd_user_pid_test_wait))
            .setPositiveButton(R.string.obd_user_pid_edit) { _, _ -> editDialog(pid) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        val tick = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                io.execute {
                    val v = ObdBridgeService.peek(pid.key)
                    val text = if (v != null) {
                        val unit = pid.unit.takeIf { it.isNotBlank() }?.let { " " + it } ?: ""
                        ctx.getString(R.string.obd_user_pid_test_value, format(v) + unit)
                    } else {
                        ctx.getString(R.string.obd_user_pid_test_none)
                    }
                    ui.post { if (dialog.isShowing) dialog.setMessage(text) }
                }
                ui.postDelayed(this, 1000)
            }
        }
        ui.post(tick)
    }

    /** Разовая проверка: отвечает ли блок на этот PID (принудительный запрос мимо кэша). */
    private fun availabilityTest(pid: UserPid) {
        val ctx = context ?: return
        val dialog = AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.obd_user_pid_test_once_title, pid.name))
            .setMessage(getString(R.string.obd_user_pid_test_once_wait))
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        io.execute {
            val res = ObdBridgeService.testPid(pid.header, pid.modeAndPid) { bytes ->
                UserPidFormula.evaluate(pid.equation, bytes)
            }
            val msg = if (res == null) {
                ctx.getString(R.string.obd_user_pid_test_once_no_link)
            } else {
                val (value, _) = res
                if (value != null) {
                    val unit = pid.unit.takeIf { it.isNotBlank() }?.let { " " + it } ?: ""
                    ctx.getString(R.string.obd_user_pid_test_once_yes, format(value) + unit)
                } else {
                    ctx.getString(R.string.obd_user_pid_test_once_no)
                }
            }
            ui.post { if (dialog.isShowing) dialog.setMessage(msg) }
        }
    }

    private fun confirmDelete(pid: UserPid) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(R.string.obd_user_pid_delete_title)
            .setMessage(getString(R.string.obd_user_pid_delete_message, pid.name))
            .setPositiveButton(R.string.obd_user_pid_delete) { _, _ ->
                UserPids.remove(requireContext(), pid.key)
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------- добавление и правка ----------------

    private fun editDialog(existing: UserPid?) {
        val ctx = context ?: return
        val dp = resources.displayMetrics.density
        fun pad(v: Int) = (v * dp).toInt()
        fun field(hint: Int, value: String?, inputType: Int = InputType.TYPE_CLASS_TEXT) = EditText(ctx).apply {
            this.hint = ctx.getString(hint)
            setText(value ?: "")
            this.inputType = inputType
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }

        val number = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED
        val nameField = field(R.string.obd_user_pid_field_name, existing?.name)
        val shortField = field(R.string.obd_user_pid_field_short, existing?.shortName)
        val pidField = field(R.string.obd_user_pid_field_pid, existing?.modeAndPid)
        pidField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        val eqField = field(R.string.obd_user_pid_field_formula, existing?.equation)
        val unitField = field(R.string.obd_user_pid_field_unit, existing?.unit)
        val minField = field(R.string.obd_user_pid_field_min, existing?.min?.let { format(it) }, number)
        val maxField = field(R.string.obd_user_pid_field_max, existing?.max?.let { format(it) }, number)
        val headerField = field(R.string.obd_user_pid_field_header, existing?.header ?: "7E0")

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(20), pad(8), pad(20), pad(8))
            listOf(nameField, shortField, pidField, eqField, unitField, minField, maxField, headerField)
                .forEach { addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        }
        val scroll = ScrollView(ctx).apply { addView(layout) }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) R.string.obd_user_pid_add else R.string.obd_user_pid_edit_title)
            .setView(scroll)
            .setPositiveButton(R.string.obd_user_pid_save, null)
            .setNeutralButton(R.string.obd_user_pid_test_once, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val pid = UserPidCodec.normalizePid(pidField.text.toString())
                if (pid == null) {
                    toast(R.string.obd_user_pid_err_pid)
                    return@setOnClickListener
                }
                val equation = eqField.text.toString().trim()
                UserPidFormula.validate(equation)?.let { error ->
                    toast(ctx.getString(R.string.obd_user_pid_err_formula, error))
                    return@setOnClickListener
                }
                val header = UserPidCodec.normalizeHeader(headerField.text.toString())
                io.execute {
                    val res = ObdBridgeService.testPid(header, pid) { bytes ->
                        UserPidFormula.evaluate(equation, bytes)
                    }
                    val msg = if (res == null) {
                        ctx.getString(R.string.obd_user_pid_test_once_no_link)
                    } else {
                        val (value, _) = res
                        if (value != null) {
                            val unit = unitField.text.toString().trim().takeIf { it.isNotBlank() }?.let { " " + it } ?: ""
                            ctx.getString(R.string.obd_user_pid_test_once_yes, format(value) + unit)
                        } else {
                            ctx.getString(R.string.obd_user_pid_test_once_no)
                        }
                    }
                    ui.post { toast(msg) }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.isEmpty()) {
                    toast(R.string.obd_user_pid_err_name)
                    return@setOnClickListener
                }
                val pid = UserPidCodec.normalizePid(pidField.text.toString())
                if (pid == null) {
                    toast(R.string.obd_user_pid_err_pid)
                    return@setOnClickListener
                }
                val equation = eqField.text.toString().trim()
                UserPidFormula.validate(equation)?.let { error ->
                    toast(ctx.getString(R.string.obd_user_pid_err_formula, error))
                    return@setOnClickListener
                }
                val min = minField.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                val max = maxField.text.toString().replace(',', '.').toDoubleOrNull() ?: 100.0
                UserPids.put(
                    requireContext(),
                    UserPid(
                        name = name,
                        shortName = shortField.text.toString().trim(),
                        modeAndPid = pid,
                        equation = equation,
                        min = min,
                        max = if (max > min) max else min + 100.0,
                        unit = unitField.text.toString().trim(),
                        header = UserPidCodec.normalizeHeader(headerField.text.toString()),
                    )
                )
                rebuild()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ---------------- импорт CSV ----------------

    private fun pickCsvFile() {
        runCatching {
            pickCsv.launch(arrayOf("text/*", "application/csv", "text/comma-separated-values", "*/*"))
        }.onFailure { toast(R.string.obd_user_pids_import_failed) }
    }

    private fun importCsv(uri: Uri) {
        val ctx = context ?: return
        toast(R.string.obd_user_pids_import_reading)
        io.execute {
            val text = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            val pids = if (text.isBlank()) emptyList() else UserPidCsv.parse(text)
            if (pids.isEmpty()) {
                ui.post { toast(R.string.obd_user_pids_import_failed) }
                return@execute
            }
            UserPids.putAll(ctx, pids)
            ui.post {
                toast(ctx.getString(R.string.obd_user_pids_import_done, pids.size))
                rebuild()
            }
        }
    }

    private fun showHelp() {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(R.string.obd_user_pids_help)
            .setMessage(R.string.obd_user_pids_help_text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(res: Int) {
        context?.let { Toast.makeText(it, res, Toast.LENGTH_SHORT).show() }
    }

    private fun toast(text: String) {
        context?.let { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() }
    }
}
