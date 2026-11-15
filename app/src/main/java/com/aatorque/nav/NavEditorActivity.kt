package com.aatorque.nav

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aatorque.obd.MazdaPids
import com.aatorque.obd.PidDef

/**
 * Редактор раскладки бета-экрана Android Auto на телефоне: предпросмотр, перемещение и размер касанием,
 * выбор датчика, добавление и удаление элементов, копирование датчиков во все формы, сохранение и сброс.
 * Изменения применяются в машине сразу после «Сохранить». При выходе с несохранёнными правками спрашивает.
 */
class NavEditorActivity : AppCompatActivity() {
    private val forms = HashMap<String, ArrayList<NavItem>>()
    private var narrowAspect = 1.15f
    private var formKey = NavLayoutStore.WIDE
    private var presetIdx = 0
    private var baseline = ""
    private var themeName = ""
    private var ticksOn = true

    private val wideAspects = floatArrayOf(2.59f, 2.27f, 2.13f)
    private val wideNames = arrayOf("Mazda, весь экран", "Mazda, с виджетом", "эмулятор")
    private val narrowAspects = floatArrayOf(0.92f, 0.73f)
    private val narrowNames = arrayOf("Mazda, колонка", "эмулятор, колонка")

    private lateinit var editor: NavEditorView
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Раскладка приборов"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        NavLayoutStore.load(this)
        loadFromStore()
        setContentView(buildUi())
        applyForm()
        applyTheme()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitEditor()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        exitEditor()
        return true
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val M_SAVE = 1
        const val M_WIDE = 2
        const val M_NARROW = 3
        const val M_PRESET = 4
        const val M_ADD = 5
        const val M_THEME = 6
        const val M_TICKS = 7
        const val M_COPY = 8
        const val M_RESET = 9
    }

    private fun currentLayout(): NavLayout {
        val map = HashMap<String, NavForm>()
        for ((key, list) in forms) map[key] = NavForm(ArrayList(list))
        return NavLayout(narrowAspect, map, themeName, ticksOn)
    }

    private fun currentJson(): String = NavLayoutStore.toJson(currentLayout())

    private fun loadFromStore() {
        forms.clear()
        val layout = NavLayoutStore.layout
        for ((key, form) in layout.forms) forms[key] = ArrayList(form.items)
        narrowAspect = layout.narrowAspect
        themeName = layout.theme
        ticksOn = layout.ticks
        baseline = currentJson()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        // предпросмотр занимает всё свободное место в портрете и в альбоме
        editor = NavEditorView(this)
        editor.forms = forms
        editor.onSelectionChanged = { updateInfo() }
        editor.onLongPressItem = { item -> showItemMenu(item) }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        info = TextView(this)
        info.textSize = 12f
        info.maxLines = 3
        info.setPadding(dp(12), dp(4), dp(12), dp(6))
        root.addView(info, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    /** Общие действия: «Сохранить» всегда на виду, остальное в меню «⋯». */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, M_SAVE, 0, "Сохранить").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(Menu.NONE, M_WIDE, 1, "Форма: широкая").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_NARROW, 2, "Форма: узкая").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_PRESET, 3, "Пропорции предпросмотра").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_ADD, 4, "Добавить…").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_THEME, 5, "Тема…").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_TICKS, 6, "Деления вкл/выкл").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_COPY, 7, "Датчики во все формы").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_RESET, 8, "Сбросить").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            M_SAVE -> save()
            M_WIDE -> {
                formKey = NavLayoutStore.WIDE
                presetIdx = 0
                applyForm()
            }
            M_NARROW -> {
                formKey = NavLayoutStore.NARROW
                presetIdx = 0
                applyForm()
            }
            M_PRESET -> {
                presetIdx += 1
                applyForm()
            }
            M_ADD -> addElement()
            M_THEME -> chooseTheme()
            M_TICKS -> toggleTicks()
            M_COPY -> confirmCopySensors()
            M_RESET -> reset()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** Меню элемента: открывается удержанием пальца на приборе или значении. */
    private fun showItemMenu(item: NavItem) {
        val gauge = item.kind == NavItem.GAUGE
        val names = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        names.add("Датчик…")
        actions.add { chooseSensor() }
        if (gauge) {
            names.add("Размер круга…")
            actions.add { chooseCircleSize() }
        }
        names.add("Удалить")
        actions.add { deleteSelected() }
        AlertDialog.Builder(this)
            .setTitle(item.label + " (" + item.pid + ")")
            .setItems(names.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /** Размер круга выбранного прибора: ползунок внизу экрана, предпросмотр не затемняется. */
    private fun chooseCircleSize() {
        val sel = editor.selectedItem() ?: return
        if (sel.kind != NavItem.GAUGE) return
        val label = TextView(this)
        val bar = SeekBar(this)
        bar.max = 70
        bar.progress = ((sel.size - 0.3f) * 100f).toInt().coerceIn(0, 70)
        label.text = "Размер круга: " + (sel.size * 100f).toInt() + "%"
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val cur = editor.selectedItem() ?: return
                val next = cur.copy(size = 0.3f + progress / 100f)
                editor.replaceSelected(next)
                label.text = "Размер круга: " + (next.size * 100f).toInt() + "%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(16), dp(24), dp(8))
        box.addView(label)
        box.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val dialog = AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton("Готово", null)
            .create()
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.setDimAmount(0f)
        dialog.show()
    }

    private fun applyForm() {
        if (forms[formKey] == null) forms[formKey] = ArrayList(NavLayoutStore.defaultLayout().form(formKey).items)
        val aspects = if (formKey == NavLayoutStore.NARROW) narrowAspects else wideAspects
        if (presetIdx >= aspects.size) presetIdx = 0
        editor.formKey = formKey
        editor.selectedId = null
        editor.aspect = aspects[presetIdx]
        updateInfo()
    }

    private fun updateInfo() {
        val narrow = formKey == NavLayoutStore.NARROW
        val names = if (narrow) narrowNames else wideNames
        val count = forms[formKey]?.size ?: 0
        val sel = editor.selectedItem()
        val selText = if (sel == null) {
            "Удержание на приборе или значении открывает меню; тяните, чтобы переместить; кружок в углу меняет размер."
        } else {
            "Выбран: " + sel.label + " (" + sel.pid + ")" +
                (if (sel.kind == NavItem.GAUGE) ", круг " + (sel.size * 100f).toInt() + "%" else "")
        }
        info.text = "Форма: " + (if (narrow) "узкая" else "широкая") + ", " + names[presetIdx] +
            ", элементов: " + count + "\n" + themeLine() + "\n" + selText
    }

    /** Новый элемент на месте старого: те же положение и размер, параметры от выбранного датчика. */
    private fun itemFromPid(old: NavItem, pd: PidDef): NavItem {
        val rpm = pd.id.equals("0c", ignoreCase = true)
        return old.copy(
            pid = pd.id,
            label = pd.shortName,
            unit = if (rpm) "x1000" else pd.unit,
            min = if (rpm) 0f else pd.min.toFloat(),
            max = if (rpm) 6f else pd.max.toFloat(),
            scale = if (rpm) 0.001 else 1.0,
            decimals = if (pd.max <= 10.0) 2 else 0
        )
    }

    private fun sensorNames(pids: List<PidDef>): Array<String> = Array(pids.size) { i ->
        val p = pids[i]
        if (p.unit.isEmpty()) p.name else p.name + ", " + p.unit
    }

    private fun chooseSensor() {
        val sel = editor.selectedItem()
        if (sel == null) {
            Toast.makeText(this, "Сначала коснитесь элемента", Toast.LENGTH_SHORT).show()
            return
        }
        val pids = MazdaPids.all
        AlertDialog.Builder(this)
            .setTitle("Датчик для «" + sel.label + "»")
            .setItems(sensorNames(pids)) { _, which -> editor.replaceSelected(itemFromPid(sel, pids[which])) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addElement() {
        AlertDialog.Builder(this)
            .setTitle("Добавить в текущую форму")
            .setItems(arrayOf("Прибор", "Значение")) { _, which ->
                pickSensorForNew(if (which == 0) NavItem.GAUGE else NavItem.INFO)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun pickSensorForNew(kind: String) {
        val pids = MazdaPids.all
        AlertDialog.Builder(this)
            .setTitle("Датчик")
            .setItems(sensorNames(pids)) { _, which -> createItem(kind, pids[which]) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /** Новый элемент по центру текущей формы; его положение и размер потом меняются касанием. */
    private fun createItem(kind: String, pd: PidDef) {
        val list = forms[formKey] ?: return
        val prefix = if (kind == NavItem.GAUGE) "g" else "i"
        var n = 0
        while (forms.values.any { l -> l.any { it.id == prefix + n } }) n++
        val id = prefix + n
        val base = if (kind == NavItem.GAUGE) {
            NavItem(id, kind, pd.id, pd.shortName, pd.unit, 0f, 1f, 1.0, 0, 0.35f, 0.25f, 0.3f, 0.5f, 0.9f)
        } else {
            NavItem(id, kind, pd.id, pd.shortName, pd.unit, 0f, 1f, 1.0, 0, 0.375f, 0.42f, 0.25f, 0.16f, 1f)
        }
        val item = itemFromPid(base, pd)
        list.add(item)
        editor.selectedId = item.id
        editor.invalidate()
        updateInfo()
    }

    private fun deleteSelected() {
        val sel = editor.selectedItem()
        if (sel == null) {
            Toast.makeText(this, "Сначала коснитесь элемента", Toast.LENGTH_SHORT).show()
            return
        }
        forms[formKey]?.removeAll { it.id == sel.id }
        editor.selectedId = null
        editor.invalidate()
        updateInfo()
    }

    private fun confirmCopySensors() {
        AlertDialog.Builder(this)
            .setTitle("Датчики во все формы")
            .setMessage("Скопировать датчики текущей формы во все остальные формы? Положение и размер элементов не изменятся. Не забудьте нажать «Сохранить».")
            .setPositiveButton("Копировать") { _, _ -> copySensorsToAll() }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /** Датчик, подпись, единицы, диапазон и формат берутся из текущей формы для элементов с тем же id в других формах. */
    private fun copySensorsToAll() {
        val src = forms[formKey] ?: return
        var changed = 0
        for ((key, list) in forms) {
            if (key == formKey) continue
            for (i in list.indices) {
                val target = list[i]
                val s = src.firstOrNull { it.id == target.id } ?: continue
                if (s.kind != target.kind) continue
                list[i] = target.copy(
                    pid = s.pid, label = s.label, unit = s.unit,
                    min = s.min, max = s.max, scale = s.scale, decimals = s.decimals
                )
                changed++
            }
        }
        editor.invalidate()
        Toast.makeText(this, "Датчики скопированы: " + changed, Toast.LENGTH_SHORT).show()
    }

    private fun themeLine(): String {
        val t = if (themeName.isEmpty()) "плоская" else themeName
        return "Тема: " + t + ", деления: " + (if (ticksOn) "да" else "нет")
    }

    /** Применить выбранную тему и деления к предпросмотру. */
    private fun applyTheme() {
        editor.setThemeRes(NavThemes.load(this, themeName), ticksOn)
        updateInfo()
    }

    private fun chooseTheme() {
        val names = NavThemes.names(this)
        val items = Array(names.size + 1) { i -> if (i == 0) "Плоская (по умолчанию)" else names[i - 1] }
        AlertDialog.Builder(this)
            .setTitle("Тема приборов AA NAV")
            .setItems(items) { _, which ->
                themeName = if (which == 0) "" else names[which - 1]
                applyTheme()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun toggleTicks() {
        ticksOn = !ticksOn
        applyTheme()
    }

    private fun save(): Boolean {
        val wide = forms[NavLayoutStore.WIDE]
        if (wide == null || wide.isEmpty()) {
            Toast.makeText(this, "В широкой форме нет ни одного элемента, сохранить нельзя", Toast.LENGTH_LONG).show()
            return false
        }
        NavLayoutStore.save(this, currentLayout())
        baseline = currentJson()
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun reset() {
        NavLayoutStore.reset(this)
        loadFromStore()
        applyForm()
        applyTheme()
        Toast.makeText(this, "Сброшено к стандартной", Toast.LENGTH_SHORT).show()
    }

    /** Назад: если есть несохранённые изменения, спросить. */
    private fun exitEditor() {
        if (currentJson() == baseline) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Есть несохранённые изменения")
            .setPositiveButton("Сохранить и выйти") { _, _ -> if (save()) finish() }
            .setNegativeButton("Выйти без сохранения") { _, _ -> finish() }
            .setNeutralButton("Остаться", null)
            .show()
    }
}
