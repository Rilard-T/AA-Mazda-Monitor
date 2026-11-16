package com.aatorque.nav

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aatorque.obd.MazdaPids
import com.aatorque.obd.PidDef
import com.aatorque.obd.PidProbe
import com.aatorque.stats.R
import java.util.Locale

/**
 * Редактор раскладки бета-экрана Android Auto на телефоне: предпросмотр, перемещение и размер касанием,
 * выбор датчика, добавление и удаление элементов, копирование датчиков во все формы, сохранение и сброс.
 * Изменения применяются в машине сразу после «Сохранить». При выходе с несохранёнными правками спрашивает.
 */
class NavEditorActivity : AppCompatActivity() {
    private val forms = HashMap<String, ArrayList<NavItem>>()
    private var narrowAspect = 1.15f
    private var formKey = NavLayoutStore.WIDE
    private var aspectSource = NavAspectSource.AUTO
    private var baseline = ""
    private var themeName = ""
    private var fontName = ""
    private var ticksOn = true
    private var debugOn = false

    private lateinit var editor: NavEditorView
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.nav_editor_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        NavLayoutStore.load(this)
        loadFromStore()
        setContentView(buildUi())
        applyForm()
        applyTheme()
        applyFont()
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
        const val M_DEBUG = 10
        const val M_FONT = 11
        const val M_COPY = 8
        const val M_RESET = 9
    }

    private fun currentLayout(): NavLayout {
        val map = HashMap<String, NavForm>()
        for ((key, list) in forms) map[key] = NavForm(ArrayList(list))
        return NavLayout(narrowAspect, map, themeName, fontName, ticksOn, debugOn)
    }

    private fun currentJson(): String = NavLayoutStore.toJson(currentLayout())

    private fun loadFromStore() {
        forms.clear()
        val layout = NavLayoutStore.layout
        for ((key, form) in layout.forms) forms[key] = ArrayList(form.items)
        narrowAspect = layout.narrowAspect
        themeName = layout.theme
        fontName = layout.font
        ticksOn = layout.ticks
        debugOn = layout.debug
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
        menu.add(Menu.NONE, M_SAVE, 0, getString(R.string.nav_save)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(Menu.NONE, M_WIDE, 1, getString(R.string.nav_form_wide)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_NARROW, 2, getString(R.string.nav_form_narrow)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_PRESET, 3, getString(R.string.nav_preset)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_ADD, 4, getString(R.string.nav_add)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_THEME, 5, getString(R.string.nav_theme)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_FONT, 6, getString(R.string.nav_font)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_TICKS, 6, getString(R.string.nav_ticks)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_DEBUG, 9, getString(R.string.nav_debug)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_COPY, 7, getString(R.string.nav_copy)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_RESET, 8, getString(R.string.nav_reset)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            M_SAVE -> save()
            M_WIDE -> {
                formKey = NavLayoutStore.WIDE
                applyForm()
            }
            M_NARROW -> {
                formKey = NavLayoutStore.NARROW
                applyForm()
            }
            M_PRESET -> cycleAspects()
            M_ADD -> addElement()
            M_THEME -> chooseTheme()
            M_FONT -> chooseFont()
            M_TICKS -> toggleTicks()
            M_DEBUG -> toggleDebug()
            M_COPY -> confirmCopySensors()
            M_RESET -> reset()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** Меню элемента: открывается удержанием пальца на приборе или значении. */
    private fun showItemMenu(item: NavItem) {
        val gauge = item.kind == NavItem.GAUGE
        val media = item.kind == NavItem.MEDIA
        val names = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        if (!media) {
            names.add(getString(R.string.nav_item_sensor))
            actions.add { chooseSensor() }
        }
        if (gauge) {
            names.add(getString(R.string.nav_item_size))
            actions.add { chooseCircleSize() }
            names.add(getString(R.string.nav_item_max))
            actions.add { chooseGaugeMax() }
            names.add(getString(R.string.nav_item_red))
            actions.add { chooseRedZone() }
        }
        names.add(getString(R.string.nav_delete))
        actions.add { deleteSelected() }
        AlertDialog.Builder(this)
            .setTitle(item.label + " (" + item.pid + ")")
            .setItems(names.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    /** Максимум шкалы выбранного прибора (скорость, обороты и т.д.): число вводится в тех же единицах, что и подписи. */
    private fun chooseGaugeMax() {
        val sel = editor.selectedItem() ?: return
        if (sel.kind != NavItem.GAUGE) return
        val hint = TextView(this)
        hint.text = getString(R.string.nav_max_hint)
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(if (sel.max == sel.max.toInt().toFloat()) sel.max.toInt().toString() else sel.max.toString())
        input.setSelection(input.text.length)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(16), dp(24), dp(8))
        box.addView(hint)
        box.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val dialog = AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton(getString(R.string.nav_apply)) { _, _ ->
                val cur = editor.selectedItem() ?: return@setPositiveButton
                val value = input.text.toString().replace(',', '.').toFloatOrNull()
                if (value == null || value <= cur.min) {
                    Toast.makeText(this, getString(R.string.nav_max_invalid), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                editor.replaceSelected(cur.copy(max = value))
                editor.invalidate()
                updateInfo()
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .create()
        dialog.show()
    }

    /** Красная зона прибора: значение, с которого шкала краснеет (0 — выключить). */
    private fun chooseRedZone() {
        val sel = editor.selectedItem() ?: return
        if (sel.kind != NavItem.GAUGE) return
        val hint = TextView(this)
        hint.text = getString(R.string.nav_red_hint)
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val cur = sel.red
        input.setText(if (cur == cur.toInt().toFloat()) cur.toInt().toString() else cur.toString())
        input.setSelection(input.text.length)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(16), dp(24), dp(8))
        box.addView(hint)
        box.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton(getString(R.string.nav_apply)) { _, _ ->
                val item = editor.selectedItem() ?: return@setPositiveButton
                val value = input.text.toString().replace(',', '.').toFloatOrNull()
                if (value == null || (value != 0f && (value <= item.min || value >= item.max))) {
                    Toast.makeText(this, getString(R.string.nav_red_invalid), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                editor.replaceSelected(item.copy(red = value))
                editor.invalidate()
                updateInfo()
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
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
        label.text = getString(R.string.nav_circle_size) + (sel.size * 100f).toInt() + "%"
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val cur = editor.selectedItem() ?: return
                val next = cur.copy(size = 0.3f + progress / 100f)
                editor.replaceSelected(next)
                label.text = getString(R.string.nav_circle_size) + (next.size * 100f).toInt() + "%"
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
            .setPositiveButton(getString(R.string.nav_done), null)
            .create()
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.setDimAmount(0f)
        dialog.show()
    }

    private fun applyForm() {
        if (forms[formKey] == null) forms[formKey] = ArrayList(NavLayoutStore.defaultLayout().form(formKey).items)
        editor.formKey = formKey
        editor.selectedId = null
        editor.aspect = currentAspect()
        updateInfo()
    }

    /**
     * Пропорции предпросмотра (ширина / высота): по умолчанию - реальная область из машины,
     * если она ещё неизвестна - экран телефона в подходящей ориентации.
     */
    private fun currentAspect(): Float {
        val dm = resources.displayMetrics
        val a = NavAspects.of(
            aspectSource, NavLayoutStore.carAspect, dm.widthPixels, dm.heightPixels,
            formKey == NavLayoutStore.NARROW
        )
        return if (a > 0f) a else NavAspects.DEFAULT
    }

    /** Переключить источник пропорций: авто → из машины → экран телефона в альбоме → в портрете. */
    private fun cycleAspects() {
        val all = NavAspectSource.values()
        aspectSource = all[(aspectSource.ordinal + 1) % all.size]
        editor.aspect = currentAspect()
        updateInfo()
    }

    private fun aspectLabel(): String = when (aspectSource) {
        NavAspectSource.AUTO -> getString(R.string.nav_aspect_auto)
        NavAspectSource.CAR -> getString(R.string.nav_aspect_car)
        NavAspectSource.LANDSCAPE -> getString(R.string.nav_aspect_landscape)
        NavAspectSource.PORTRAIT -> getString(R.string.nav_aspect_portrait)
    }

    private fun updateInfo() {
        val narrow = formKey == NavLayoutStore.NARROW
        val count = forms[formKey]?.size ?: 0
        val sel = editor.selectedItem()
        val selText = if (sel == null) {
            getString(R.string.nav_help)
        } else {
            getString(R.string.nav_selected) + sel.label + " (" + sel.pid + ")" +
                (if (sel.kind == NavItem.GAUGE) getString(R.string.nav_circle) + (sel.size * 100f).toInt() + "%" else "")
        }
        info.text = getString(R.string.nav_form) + (if (narrow) getString(R.string.nav_narrow) else getString(R.string.nav_wide)) +
            getString(R.string.nav_aspect_prefix) + aspectLabel() + " " +
            String.format(Locale.US, "%.2f", editor.aspect) +
            getString(R.string.nav_items) + count + "\n" + themeLine() + "\n" + selText
    }

    /** Новый элемент на месте старого: те же положение и размер, параметры от выбранного датчика. */
    private fun itemFromPid(old: NavItem, pd: PidDef): NavItem {
        val rpm = pd.id.equals("0c", ignoreCase = true)
        return old.copy(
            pid = pd.id,
            label = getString(pd.shortRes),
            unit = if (rpm) "x1000" else unitOf(pd),
            min = if (rpm) 0f else pd.min.toFloat(),
            max = if (rpm) 6f else pd.max.toFloat(),
            scale = if (rpm) 0.001 else 1.0,
            decimals = if (pd.max <= 10.0) 2 else 0
        )
    }

    /** Единицы из ресурсов: 0 означает, что единиц у датчика нет. */
    private fun unitOf(pd: PidDef): String = if (pd.unitRes != 0) getString(pd.unitRes) else ""

    private fun sensorNames(pids: List<PidDef>): Array<String> = Array(pids.size) { i ->
        val p = pids[i]
        var name = getString(p.nameRes)
        if (PidProbe.isMissing(p.id)) name += " · " + getString(R.string.nav_no_answer)
        val unit = unitOf(p)
        if (unit.isEmpty()) name else name + ", " + unit
    }

    private fun chooseSensor() {
        val sel = editor.selectedItem()
        if (sel == null) {
            Toast.makeText(this, getString(R.string.nav_touch_first), Toast.LENGTH_SHORT).show()
            return
        }
        val pids = MazdaPids.all
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_sensor_for) + sel.label + "»")
            .setItems(sensorNames(pids)) { _, which -> editor.replaceSelected(itemFromPid(sel, pids[which])) }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun addElement() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_add_current))
            .setItems(arrayOf(getString(R.string.nav_kind_gauge), getString(R.string.nav_kind_info), getString(R.string.nav_kind_media))) { _, which ->
                if (which == 2) createMediaItem() else pickSensorForNew(if (which == 0) NavItem.GAUGE else NavItem.INFO)
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun pickSensorForNew(kind: String) {
        val pids = MazdaPids.all
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_sensor))
            .setItems(sensorNames(pids)) { _, which -> createItem(kind, pids[which]) }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    /** Новый элемент по центру текущей формы; его положение и размер потом меняются касанием. */
    private fun createItem(kind: String, pd: PidDef) {
        val list = forms[formKey] ?: return
        val prefix = if (kind == NavItem.GAUGE) "g" else "i"
        var n = 0
        while (forms.values.any { l -> l.any { it.id == prefix + n } }) n++
        val id = prefix + n
        val label = getString(pd.shortRes)
        val unit = unitOf(pd)
        val base = if (kind == NavItem.GAUGE) {
            NavItem(id, kind, pd.id, label, unit, 0f, 1f, 1.0, 0, 0.35f, 0.25f, 0.3f, 0.5f, 0.9f)
        } else {
            NavItem(id, kind, pd.id, label, unit, 0f, 1f, 1.0, 0, 0.375f, 0.42f, 0.25f, 0.16f, 1f)
        }
        val item = itemFromPid(base, pd)
        list.add(item)
        editor.selectedId = item.id
        editor.invalidate()
        updateInfo()
    }

    /** Новый элемент-медиа (название играющего трека) по центру текущей формы. */
    private fun createMediaItem() {
        val list = forms[formKey] ?: return
        var n = 0
        while (forms.values.any { l -> l.any { it.id == "m" + n } }) n++
        val id = "m" + n
        val item = NavItem(id, NavItem.MEDIA, "media_title", "Трек", "", 0f, 1f, 1.0, 0, 0.375f, 0.42f, 0.25f, 0.16f, 1f)
        list.add(item)
        editor.selectedId = item.id
        editor.invalidate()
        updateInfo()
    }

    private fun deleteSelected() {
        val sel = editor.selectedItem()
        if (sel == null) {
            Toast.makeText(this, getString(R.string.nav_touch_first), Toast.LENGTH_SHORT).show()
            return
        }
        forms[formKey]?.removeAll { it.id == sel.id }
        editor.selectedId = null
        editor.invalidate()
        updateInfo()
    }

    private fun confirmCopySensors() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_copy))
            .setMessage(getString(R.string.nav_copy_message))
            .setPositiveButton(getString(R.string.nav_copy_action)) { _, _ -> copySensorsToAll() }
            .setNegativeButton(getString(R.string.nav_cancel), null)
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
        val t = if (themeName.isEmpty()) getString(R.string.nav_flat) else themeName
        return getString(R.string.nav_theme_prefix) + t + getString(R.string.nav_ticks_prefix) + (if (ticksOn) getString(R.string.nav_yes) else getString(R.string.nav_no)) + getString(R.string.nav_debug_prefix) + (if (debugOn) getString(R.string.nav_on) else getString(R.string.nav_off))
    }

    /** Применить выбранную тему и деления к предпросмотру. */
    private fun applyTheme() {
        editor.setThemeRes(NavThemes.load(this, themeName), ticksOn)
        updateInfo()
    }

    private fun applyFont() {
        editor.setFont(NavFonts.load(this, fontName))
        updateInfo()
    }

    private fun chooseFont() {
        val entries = resources.getStringArray(R.array.fontEntries)
        val values = resources.getStringArray(R.array.fontValues)
        val items = Array(entries.size + 1) { i -> if (i == 0) getString(R.string.nav_font_default) else entries[i - 1] }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_font_dialog))
            .setItems(items) { _, which ->
                fontName = if (which == 0) "" else values[which - 1]
                applyFont()
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun chooseTheme() {
        val names = NavThemes.names(this)
        val items = Array(names.size + 1) { i -> if (i == 0) getString(R.string.nav_theme_default) else names[i - 1] }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_theme_dialog))
            .setItems(items) { _, which ->
                themeName = if (which == 0) "" else names[which - 1]
                applyTheme()
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun toggleTicks() {
        ticksOn = !ticksOn
        applyTheme()
    }

    private fun toggleDebug() {
        debugOn = !debugOn
        updateInfo()
    }

    private fun save(): Boolean {
        val wide = forms[NavLayoutStore.WIDE]
        if (wide == null || wide.isEmpty()) {
            Toast.makeText(this, getString(R.string.nav_nothing_wide), Toast.LENGTH_LONG).show()
            return false
        }
        NavLayoutStore.save(this, currentLayout())
        baseline = currentJson()
        Toast.makeText(this, getString(R.string.nav_saved), Toast.LENGTH_SHORT).show()
        return true
    }

    private fun reset() {
        NavLayoutStore.reset(this)
        loadFromStore()
        applyForm()
        applyTheme()
        Toast.makeText(this, getString(R.string.nav_reset_done), Toast.LENGTH_SHORT).show()
    }

    /** Назад: если есть несохранённые изменения, спросить. */
    private fun exitEditor() {
        if (currentJson() == baseline) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nav_unsaved))
            .setPositiveButton(getString(R.string.nav_save_exit)) { _, _ -> if (save()) finish() }
            .setNegativeButton(getString(R.string.nav_exit_no_save)) { _, _ -> finish() }
            .setNeutralButton(getString(R.string.nav_stay), null)
            .show()
    }
}
