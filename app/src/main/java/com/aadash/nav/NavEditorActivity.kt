package com.aadash.nav

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aadash.obd.MazdaPids
import com.aadash.obd.PidDef
import com.aadash.obd.PidProbe
import com.aadash.stats.NotiService
import com.aadash.stats.R
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
    private var aspectSource = NavAspectSource.LANDSCAPE
    private var previewIndex = -1
    private var baseline = ""
    private var themeName = ""
    private var fontName = ""
    private var ticksOn = true
    private var debugOn = false
    private var graphs: ArrayList<GraphDef> = ArrayList()

    private lateinit var editor: NavEditorView
    private lateinit var info: TextView
    private lateinit var previewSwitch: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.nav_editor_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        NavLayoutStore.load(this)
        loadFromStore()
        setContentView(buildUi())
        updatePreviewSwitch()
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
        const val M_GRAPHS = 12
        const val M_GRID = 13
        const val M_SNAP = 14
        const val M_ALIGN = 15
        const val M_RESET = 9
    }

    private fun currentLayout(): NavLayout {
        val map = HashMap<String, NavForm>()
        for ((key, list) in forms) map[key] = NavForm(ArrayList(list))
        return NavLayout(narrowAspect, map, ArrayList(graphs), themeName, fontName, ticksOn, debugOn)
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
        graphs = ArrayList(layout.graphs)
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

        previewSwitch = Button(this)
        previewSwitch.textSize = 12f
        previewSwitch.setPadding(dp(12), dp(4), dp(12), dp(4))
        previewSwitch.setOnClickListener { togglePreview() }
        root.addView(previewSwitch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
        menu.add(Menu.NONE, M_GRAPHS, 9, "Графики").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_GRID, 10, "Сетка: " + if (editor.gridOn) "вкл" else "выкл").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_SNAP, 11, "Привязка: " + if (editor.snapToGrid) "вкл" else "выкл").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.NONE, M_ALIGN, 12, "Выровнять").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
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
            M_GRAPHS -> manageGraphs()
            M_GRID -> { editor.gridOn = !editor.gridOn; editor.invalidate(); updateInfo(); invalidateOptionsMenu() }
            M_SNAP -> { editor.snapToGrid = !editor.snapToGrid; editor.invalidate(); updateInfo(); invalidateOptionsMenu() }
            M_ALIGN -> alignSelected()
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
        names.add("Границы виджета")
        actions.add { chooseBorder() }
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
        editor.previewGraph = if (previewIndex >= 0) graphs.getOrNull(previewIndex) else null
        updateInfo()
        updatePreviewSwitch()
    }

    private fun togglePreview() {
        if (graphs.isEmpty()) {
            Toast.makeText(this, "Сначала добавьте график (меню ⋯ → Графики)", Toast.LENGTH_SHORT).show()
            return
        }
        previewIndex++
        if (previewIndex >= graphs.size) previewIndex = -1
        editor.previewGraph = if (previewIndex >= 0) graphs.getOrNull(previewIndex) else null
        editor.invalidate()
        updateInfo()
        updatePreviewSwitch()
    }

    private fun updatePreviewSwitch() {
        if (!::previewSwitch.isInitialized) return
        val label = if (previewIndex < 0) {
            "Предпросмотр: приборы"
        } else {
            "Предпросмотр: " + (graphs.getOrNull(previewIndex)?.name ?: "график")
        }
        previewSwitch.text = label
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

    /** Переключить пропорции предпросмотра: телефон альбом ↔ телефон портрет. */
    private fun cycleAspects() {
        aspectSource = if (aspectSource == NavAspectSource.LANDSCAPE) NavAspectSource.PORTRAIT else NavAspectSource.LANDSCAPE
        editor.aspect = currentAspect()
        updateInfo()
    }

    private fun aspectLabel(): String = when (aspectSource) {
        NavAspectSource.PORTRAIT -> getString(R.string.nav_aspect_portrait)
        else -> getString(R.string.nav_aspect_landscape)
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
        // Обороты как прибор (GAUGE) остаются в тысячах (x1000), как на шкале 0..8;
        // текстовое значение (INFO) показываем обычным числом (2750).
        val rpmGauge = rpm && old.kind == NavItem.GAUGE
        return old.copy(
            pid = pd.id,
            label = pd.short(this),
            unit = if (rpmGauge) "x1000" else unitOf(pd),
            min = if (rpmGauge) 0f else pd.min.toFloat(),
            max = if (rpmGauge) 8f else pd.max.toFloat(),
            scale = if (rpmGauge) 0.001 else 1.0,
            decimals = if (pd.max <= 10.0) 2 else 0
        )
    }

    /** Единицы датчика: пустая строка означает, что их нет. */
    private fun unitOf(pd: PidDef): String = pd.unit(this)

    private fun sensorNames(pids: List<PidDef>): Array<String> = Array(pids.size) { i ->
        val p = pids[i]
        var name = p.name(this)
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
        val label = pd.short(this)
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
        if (!NotiService.isNotificationAccessEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.media_access_title))
                .setMessage(getString(R.string.media_access_summary))
                .setPositiveButton("Открыть настройки") { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                putExtra(
                                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                    NotiService::class.java.canonicalName
                                )
                            }
                        }
                    )
                }
                .setNegativeButton(getString(R.string.nav_cancel), null)
                .show()
        }
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

    /** Управление экранами-графиками: список, добавление, удаление, цвета. */
    private fun manageGraphs() {
        val names = graphs.map { it.name + " (" + it.pids.size + " датч.)" }.toTypedArray()
        val items = names + "+ Добавить график"
        AlertDialog.Builder(this)
            .setTitle("Графики")
            .setItems(items) { _, which ->
                if (which == graphs.size) addGraph() else editGraph(which)
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun addGraph() {
        val pids = MazdaPids.all
        val checked = BooleanArray(pids.size)
        AlertDialog.Builder(this)
            .setTitle("Выберите 2–4 датчика")
            .setMultiChoiceItems(Array(pids.size) { i ->
                val pd = pids[i]
                var name = pd.name(this)
                val unit = pd.unit(this)
                if (unit.isNotEmpty()) name += ", $unit"
                name
            }, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Далее") { _, _ ->
                val sel = pids.filterIndexed { i, _ -> checked[i] }
                if (sel.size < 2 || sel.size > 4) {
                    Toast.makeText(this, "Нужно от 2 до 4 датчиков", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                finishAddGraph(sel.map { it.id })
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun finishAddGraph(pidIds: List<String>) {
        val input = EditText(this)
        input.hint = "Название графика"
        input.setText("График ${graphs.size + 1}")
        val windows = arrayOf("2", "5", "10", "30")
        var windowSel = 1
        val spinner = android.widget.Spinner(this)
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, windows)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(windowSel)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(16), dp(24), dp(8))
        box.addView(TextView(this).apply { text = "Название" })
        box.addView(input)
        box.addView(TextView(this).apply { text = "Окно, мин" })
        box.addView(spinner)
        AlertDialog.Builder(this)
            .setTitle("Новый график")
            .setView(box)
            .setPositiveButton("Добавить") { _, _ ->
                val name = input.text.toString().ifBlank { "График ${graphs.size + 1}" }
                val w = windows[spinner.selectedItemPosition].toIntOrNull() ?: 5
                val palette = intArrayOf(0xFF3AA6FF.toInt(), 0xFFFFBE3C.toInt(), 0xFF7CFF6B.toInt(), 0xFFFF5A4A.toInt())
                val colors = pidIds.mapIndexed { i, _ -> palette[i % palette.size] }
                graphs.add(GraphDef(
                    id = "gr" + System.currentTimeMillis(),
                    name = name,
                    pids = pidIds,
                    colors = colors,
                    windowMin = w
                ))
                editor.invalidate()
                updateInfo()
                updatePreviewSwitch()
                Toast.makeText(this, "График добавлен", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun editGraph(index: Int) {
        val g = graphs[index]
        val names = arrayOf("Переименовать", "Изменить цвета", "Удалить")
        AlertDialog.Builder(this)
            .setTitle(g.name)
            .setItems(names) { _, which ->
                when (which) {
                    0 -> renameGraph(index)
                    1 -> recolorGraph(index)
                    2 -> { graphs.removeAt(index); if (previewIndex >= graphs.size) previewIndex = graphs.size - 1; editor.previewGraph = if (previewIndex >= 0) graphs.getOrNull(previewIndex) else null; editor.invalidate(); updateInfo(); updatePreviewSwitch() }
                }
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun renameGraph(index: Int) {
        val g = graphs[index]
        val input = EditText(this)
        input.setText(g.name)
        AlertDialog.Builder(this)
            .setTitle("Название")
            .setView(input)
            .setPositiveButton(getString(R.string.nav_apply)) { _, _ ->
                val name = input.text.toString().ifBlank { g.name }
                graphs[index] = g.copy(name = name)
                editor.invalidate()
                updateInfo()
                updatePreviewSwitch()
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun recolorGraph(index: Int) {
        val g = graphs[index]
        val pids = g.pids
        if (pids.isEmpty()) return
        val palette = intArrayOf(0xFF3AA6FF.toInt(), 0xFFFFBE3C.toInt(), 0xFF7CFF6B.toInt(), 0xFFFF5A4A.toInt(), 0xFFCC66FF.toInt(), 0xFF00CCFF.toInt())
        val colorNames = arrayOf("Синий", "Жёлтый", "Зелёный", "Красный", "Фиолетовый", "Голубой")
        // Нормализуем список цветов под количество датчиков.
        val colors = ArrayList<Int>()
        for (i in pids.indices) colors.add(g.colors.getOrElse(i) { palette[i % palette.size] })

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), dp(8))
        val spinners = ArrayList<Spinner>()
        for (i in pids.indices) {
            val pid = pids[i]
            val name = MazdaPids.byId(pid)?.name(this) ?: pid
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            val tv = TextView(this)
            tv.text = name
            tv.setPadding(0, 0, dp(8), 0)
            val lpText = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(tv, lpText)
            val spinner = Spinner(this)
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            val curIdx = palette.indexOf(colors[i]).let { if (it < 0) i % palette.size else it }
            spinner.setSelection(curIdx.coerceIn(0, palette.size - 1))
            spinners.add(spinner)
            val lpSpinner = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            row.addView(spinner, lpSpinner)
            box.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(6) })
        }

        AlertDialog.Builder(this)
            .setTitle("Цвета графика")
            .setView(box)
            .setPositiveButton("Применить") { _, _ ->
                val newColors = spinners.map { spinner -> palette[spinner.selectedItemPosition] }
                graphs[index] = g.copy(colors = newColors)
                editor.invalidate()
                updateInfo()
                Toast.makeText(this, "Цвета обновлены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun chooseBorder() {
        val sel = editor.selectedItem() ?: return
        val options = arrayOf("Круг", "Квадрат", "Без рамки")
        val vals = arrayOf("circle", "square", "none")
        val cur = sel.border.ifEmpty { if (sel.kind == NavItem.GAUGE) "circle" else "square" }
        val checked = vals.indexOf(cur).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Границы виджета")
            .setSingleChoiceItems(options, checked) { d, which ->
                editor.replaceSelected(sel.copy(border = vals[which]))
                editor.invalidate()
                updateInfo()
                d.dismiss()
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
    }

    private fun alignSelected() {
        val sel = editor.selectedItem() ?: run {
            Toast.makeText(this, getString(R.string.nav_touch_first), Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf("По левому краю", "По центру по горизонтали", "По центру по вертикали", "По правому краю")
        AlertDialog.Builder(this)
            .setTitle("Выровнять")
            .setItems(options) { _, which ->
                val cur = editor.selectedItem() ?: return@setItems
                val nx = when (which) {
                    0 -> 0f
                    1 -> ((1f - cur.w) / 2f).coerceAtLeast(0f)
                    2 -> cur.x
                    3 -> (1f - cur.w).coerceAtLeast(0f)
                    else -> cur.x
                }
                val ny = when (which) {
                    2 -> ((1f - cur.h) / 2f).coerceAtLeast(0f)
                    else -> cur.y
                }
                editor.replaceSelected(cur.copy(x = nx, y = ny))
                editor.invalidate()
                updateInfo()
            }
            .setNegativeButton(getString(R.string.nav_cancel), null)
            .show()
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
