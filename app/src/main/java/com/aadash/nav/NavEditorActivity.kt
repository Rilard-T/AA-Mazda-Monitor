package com.aadash.nav

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.aadash.obd.MazdaPids
import com.google.android.material.button.MaterialButton
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
    // По умолчанию — реальная область из машины, если Android Auto её уже сообщал:
    // именно в этих пропорциях раскладку потом увидят на экране ГУ.
    private var aspectSource = NavAspectSource.AUTO
    private var previewIndex = -1
    private var baseline = ""
    private var themeName = ""
    private var fontName = ""
    private var ticksOn = true
    private var debugOn = false
    private var regenBanner: RegenBanner = RegenBanner.DEFAULT
    private var graphs: ArrayList<GraphDef> = ArrayList()

    private lateinit var editor: NavEditorView
    private var info: TextView? = null
    private var previewMenuItem: MenuItem? = null
    /** Кнопки панели с состоянием вкл/выкл: пара с функцией, которая говорит, активна ли кнопка. */
    private val stateButtons = ArrayList<Pair<MaterialButton, () -> Boolean>>()
    private var gridBtn: MaterialButton? = null
    private var snapBtn: MaterialButton? = null
    private var ticksBtn: MaterialButton? = null
    private var debugBtn: MaterialButton? = null
    private var regenBtn: MaterialButton? = null
    private var regenPosBtn: MaterialButton? = null
    private var regenFillBtn: MaterialButton? = null
    private var regenTextBtn: MaterialButton? = null
    private var regenBorderBtn: MaterialButton? = null
    private var regenSizeBtn: MaterialButton? = null
    private var regenPlaceBtn: MaterialButton? = null
    private var aspectBtn: MaterialButton? = null
    private lateinit var previewFrame: NavAspectFrame

    override fun onCreate(savedInstanceState: Bundle?) {
        com.aadash.prefs.AppAccent.applyTo(this)
        super.onCreate(savedInstanceState)
        // Короткий заголовок: полный («Раскладка приборов») не влезает рядом с «Сохранить»
        title = "Раскладка"
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
        const val M_PREVIEW = 16
        const val M_RESET = 9
    }

    private fun currentLayout(): NavLayout {
        val map = HashMap<String, NavForm>()
        for ((key, list) in forms) map[key] = NavForm(ArrayList(list))
        return NavLayout(narrowAspect, map, ArrayList(graphs), themeName, fontName, ticksOn, debugOn, regenBanner)
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
        regenBanner = layout.regen
        graphs = ArrayList(layout.graphs)
        baseline = currentJson()
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Цвет из темы приложения (тема не меняется, берём готовые токены). */
    private fun themeColor(attr: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (theme.resolveAttribute(attr, value, true)) value.data else fallback
    }

    /**
     * Экран редактора: предпросмотр ровно в пропорциях машины (без пустых полос) и панель управления.
     * В альбоме панель справа от предпросмотра, в портрете - снизу.
     */
    private fun buildUi(): View {
        val landscape = isLandscape()
        stateButtons.clear()
        val root = LinearLayout(this)
        root.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        root.setBackgroundColor(themeColor(android.R.attr.colorBackground, Color.TRANSPARENT))
        root.setPadding(dp(12), dp(6), dp(12), dp(12))

        editor = NavEditorView(this)
        editor.forms = forms
        editor.regen = regenBanner
        // Плашку можно перетащить прямо в предпросмотре — кнопка сдвига обновится сразу.
        editor.onRegenChanged = { moved ->
            regenBanner = moved
            regenPosBtn?.text = regenPosLabel()
        }
        editor.onSelectionChanged = { updateInfo() }
        editor.onLongPressItem = { item -> showItemMenu(item) }

        previewFrame = NavAspectFrame(this)
        previewFrame.aspect = currentAspect()
        // Портрет телефона как пропорция предпросмотра даёт очень высокий холст: без ограничения он
        // вытеснял панель с кнопками за нижний край экрана. Ширину предпросмотр считает сам.
        previewFrame.maxHeight = if (landscape) 0 else (resources.displayMetrics.heightPixels * 0.45f).toInt()
        previewFrame.elevation = dp(4).toFloat()
        previewFrame.clipToOutline = true
        previewFrame.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.rgb(24, 26, 32))
        }
        previewFrame.addView(
            editor,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val holder = FrameLayout(this)
        holder.addView(
            previewFrame,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (landscape) Gravity.CENTER else Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL

        // Аккордеон: пункты бывшего меню «⋯» сгруппированы и доступны прямо в панели.
        val scroll = ScrollView(this)
        val acc = LinearLayout(this)
        acc.orientation = LinearLayout.VERTICAL
        scroll.addView(acc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Заголовок группы — кнопка на тональной подложке, содержимое — карточка под ней.
        fun addGroup(title: String, initiallyOpen: Boolean, build: (LinearLayout) -> Unit) {
            val header = MaterialButton(this)
            header.backgroundTintList = ColorStateList.valueOf(
                themeColor(com.google.android.material.R.attr.colorSecondaryContainer, Color.rgb(230, 232, 240))
            )
            header.setTextColor(
                themeColor(com.google.android.material.R.attr.colorOnSecondaryContainer, Color.BLACK)
            )
            header.isAllCaps = false
            header.text = groupTitle(title, initiallyOpen)
            header.textSize = 14f
            header.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            header.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            header.insetTop = 0
            header.insetBottom = 0
            header.minHeight = dp(44)
            header.cornerRadius = dp(12)
            header.setPaddingRelative(dp(10), 0, dp(10), 0)
            val content = LinearLayout(this)
            content.orientation = LinearLayout.VERTICAL
            content.visibility = if (initiallyOpen) View.VISIBLE else View.GONE
            content.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.rgb(40, 42, 48)))
            }
            content.setPadding(dp(10), dp(10), dp(10), dp(10))
            build(content)
            header.setOnClickListener {
                val nowOpen = content.visibility != View.VISIBLE
                content.visibility = if (nowOpen) View.VISIBLE else View.GONE
                header.text = groupTitle(title, nowOpen)
            }
            acc.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            acc.addView(
                content,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(4) }
            )
            // Отступа между заголовками не было: при свёрнутых группах соседние заголовки одного
            // цвета слипались в одну плашку. Разделитель-прослойка остаётся видимой в любом состоянии.
            acc.addView(Space(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
        }

        fun mkBtn(label: String, onClick: () -> Unit): MaterialButton {
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            b.isAllCaps = false
            b.text = label
            b.textSize = 14f
            b.insetTop = 0
            b.insetBottom = 0
            b.minHeight = dp(48)
            b.cornerRadius = dp(12)
            b.strokeWidth = dp(1)
            b.setOnClickListener { onClick() }
            return b
        }

        // Кнопка с состоянием: включённая заливается акцентом, выключенная остаётся контурной.
        fun stateBtn(btn: MaterialButton, active: () -> Boolean): MaterialButton {
            stateButtons.add(btn to active)
            styleState(btn, active())
            return btn
        }

        fun rowOf(vararg buttons: MaterialButton): LinearLayout {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (b in buttons) r.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
            return r
        }

        // Группа «Форма»
        addGroup("Форма", true) { g ->
            val wideBtn = mkBtn(getString(R.string.nav_form_wide)) { formKey = NavLayoutStore.WIDE; applyForm() }
            val narrowBtn = mkBtn(getString(R.string.nav_form_narrow)) { formKey = NavLayoutStore.NARROW; applyForm() }
            stateBtn(wideBtn) { formKey == NavLayoutStore.WIDE }
            stateBtn(narrowBtn) { formKey == NavLayoutStore.NARROW }
            g.addView(rowOf(wideBtn, narrowBtn))
        }

        // Группа «Пропорции»
        addGroup("Пропорции", false) { g ->
            aspectBtn = mkBtn("Пресет: " + aspectLabel()) { cycleAspects(); aspectBtn?.text = "Пресет: " + aspectLabel() }
            g.addView(aspectBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Группа «Вид»
        addGroup("Вид", false) { g ->
            ticksBtn = mkBtn(if (ticksOn) getString(R.string.nav_ticks) + ": вкл" else getString(R.string.nav_ticks) + ": выкл") { toggleTicks(); ticksBtn?.text = if (ticksOn) getString(R.string.nav_ticks) + ": вкл" else getString(R.string.nav_ticks) + ": выкл" }
            debugBtn = mkBtn(if (debugOn) getString(R.string.nav_debug) + ": вкл" else getString(R.string.nav_debug) + ": выкл") { toggleDebug(); debugBtn?.text = if (debugOn) getString(R.string.nav_debug) + ": вкл" else getString(R.string.nav_debug) + ": выкл" }
            ticksBtn?.let { stateBtn(it) { ticksOn } }
            debugBtn?.let { stateBtn(it) { debugOn } }
            val themeBtn = mkBtn(getString(R.string.nav_theme)) { chooseTheme() }
            val fontBtn = mkBtn(getString(R.string.nav_font)) { chooseFont() }
            g.addView(ticksBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            g.addView(debugBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            g.addView(themeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            g.addView(fontBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            // Уведомление о прожиге: его вид виден в предпросмотре сразу.
            g.addView(TextView(this).apply {
                setText("Уведомление о прожиге")
                textSize = 13f
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY))
                setPadding(dp(4), dp(10), 0, dp(2))
            })

            regenBtn = mkBtn(regenOnLabel()) {
                regenBanner = regenBanner.copy(enabled = !regenBanner.enabled)
                applyRegen()
            }
            regenBtn?.let { stateBtn(it) { regenBanner.enabled } }
            regenPosBtn = mkBtn(regenPosLabel()) { chooseRegenShift() }
            regenFillBtn = mkBtn(regenColorLabel("Фон", regenBanner.fill)) {
                chooseRegenColor("Цвет фона", regenBanner.fill) { regenBanner = regenBanner.copy(fill = it); applyRegen() }
            }
            regenTextBtn = mkBtn(regenColorLabel("Текст", regenBanner.text)) {
                chooseRegenColor("Цвет текста", regenBanner.text) { regenBanner = regenBanner.copy(text = it); applyRegen() }
            }
            regenBorderBtn = mkBtn(regenColorLabel("Рамка", regenBanner.border)) {
                chooseRegenColor("Цвет рамки", regenBanner.border) { regenBanner = regenBanner.copy(border = it); applyRegen() }
            }
            regenSizeBtn = mkBtn(regenSizeLabel()) { chooseRegenSize() }
            regenPlaceBtn = mkBtn(regenPlaceLabel()) {
                regenBanner = regenBanner.copy(
                    placement = if (regenBanner.placement == RegenBanner.PLACEMENT_HEADER) RegenBanner.PLACEMENT_FIRST_ROW else RegenBanner.PLACEMENT_HEADER
                )
                applyRegen()
            }
            for (b in listOf(regenBtn, regenPosBtn, regenFillBtn, regenTextBtn, regenBorderBtn, regenSizeBtn, regenPlaceBtn)) {
                g.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }

        // Группа «Элементы»
        addGroup("Элементы", false) { g ->
            val addBtn = mkBtn(getString(R.string.nav_add)) { addElement() }
            val alignBtn = mkBtn("Выровнять") { alignSelected() }
            val borderBtn = mkBtn("Границы виджета") { if (editor.selectedItem() != null) chooseBorder() }
            g.addView(addBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            g.addView(alignBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            g.addView(borderBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Группа «Графики»
        addGroup("Графики", false) { g ->
            val graphsBtn = mkBtn("Графики") { manageGraphs() }
            g.addView(graphsBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Группа «Инструменты»
        addGroup("Инструменты", false) { g ->
            gridBtn = mkBtn("Сетка: " + if (editor.gridOn) "вкл" else "выкл") { editor.gridOn = !editor.gridOn; editor.invalidate(); updateInfo(); gridBtn?.text = "Сетка: " + if (editor.gridOn) "вкл" else "выкл"; refreshStateButtons() }
            snapBtn = mkBtn("Привязка: " + if (editor.snapToGrid) "вкл" else "выкл") { editor.snapToGrid = !editor.snapToGrid; editor.invalidate(); updateInfo(); snapBtn?.text = "Привязка: " + if (editor.snapToGrid) "вкл" else "выкл"; refreshStateButtons() }
            gridBtn?.let { stateBtn(it) { editor.gridOn } }
            snapBtn?.let { stateBtn(it) { editor.snapToGrid } }
            val copyBtn = mkBtn(getString(R.string.nav_copy)) { confirmCopySensors() }
            val resetBtn = mkBtn(getString(R.string.nav_reset)) { reset() }
            g.addView(gridBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            g.addView(snapBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            g.addView(copyBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            g.addView(resetBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        if (landscape) {
            root.addView(holder, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            root.addView(
                panel,
                LinearLayout.LayoutParams(dp(240), ViewGroup.LayoutParams.MATCH_PARENT).apply { marginStart = dp(12) }
            )
        } else {
            // Предпросмотр сверху (а не в середине экрана), панель с аккордеоном — под ним.
            root.addView(
                holder,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            root.addView(
                panel,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) }
            )
        }
        refreshStateButtons()
        return root
    }

    private fun regenOnLabel(): String = "Прожиг: " + if (regenBanner.enabled) "вкл" else "выкл"

    private fun regenPosLabel(): String = "Сдвиг: " + Math.round(regenBanner.x * 100f) + " %"

    private fun regenColorLabel(name: String, color: Int): String =
        name + ": " + String.format("#%06X", 0xFFFFFF and color)

    private fun regenSizeLabel(): String = "Размер: " + Math.round(regenBanner.scale * 100f) + "%"

    private fun regenPlaceLabel(): String =
        "Место: " + if (regenBanner.placement == RegenBanner.PLACEMENT_HEADER) "шапка" else "первая строка"

    /** Изменение вида уведомления: сразу в предпросмотр и на кнопки. */
    private fun applyRegen() {
        editor.regen = regenBanner
        regenBtn?.text = regenOnLabel()
        regenPosBtn?.text = regenPosLabel()
        regenFillBtn?.text = regenColorLabel("Фон", regenBanner.fill)
        regenTextBtn?.text = regenColorLabel("Текст", regenBanner.text)
        regenBorderBtn?.text = regenColorLabel("Рамка", regenBanner.border)
        regenSizeBtn?.text = regenSizeLabel()
        regenPlaceBtn?.text = regenPlaceLabel()
        refreshStateButtons()
    }

    /**
     * Сдвиг плашки в строке шапки по горизонтали: 0 % — к левому краю, 100 % — к правому.
     * Тот же сдвиг можно задать пальцем в предпросмотре.
     */
    private fun chooseRegenShift() {
        val seek = SeekBar(this)
        seek.max = 100
        seek.progress = Math.round(regenBanner.x * 100f).coerceIn(0, 100)
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), dp(8))
        box.addView(
            seek,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                regenBanner = regenBanner.copy(x = progress / 100f)
                applyRegen()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        AlertDialog.Builder(this)
            .setTitle("Сдвиг уведомления по горизонтали")
            .setView(box)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun chooseRegenColor(title: String, current: Int, onPick: (Int) -> Unit) {
        val picker = com.rarepebble.colorpicker.ColorPickerView(this).apply {
            showHex(true)
            color = current
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(picker)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                onPick(picker.color)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /** Размер плашки: 60–200 % шагом 10, текст и отступы масштабируются вместе с ней. */
    private fun chooseRegenSize() {
        val maxStep = Math.round((RegenBanner.MAX_SCALE - RegenBanner.MIN_SCALE) / 0.1f)
        val before = regenBanner
        val seek = SeekBar(this)
        seek.max = maxStep
        seek.progress = Math.round((regenBanner.scale - RegenBanner.MIN_SCALE) / 0.1f).coerceIn(0, maxStep)
        val holder = FrameLayout(this)
        holder.setPadding(dp(20), dp(8), dp(20), dp(8))
        holder.addView(
            seek,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle("Размер уведомления: " + Math.round(regenBanner.scale * 100f) + "%")
            .setView(holder)
            .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                regenBanner = before
                applyRegen()
                d.dismiss()
            }
            .create()
        // Значение меняется сразу: и плашка в предпросмотре, и надпись на кнопке.
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                regenBanner = regenBanner.copy(scale = RegenBanner.MIN_SCALE + progress * 0.1f)
                applyRegen()
                dialog.setTitle("Размер уведомления: " + Math.round(regenBanner.scale * 100f) + "%")
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {}

            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        dialog.show()
    }

    private fun groupTitle(title: String, open: Boolean): String =
        title + if (open) "    ▾" else "    ▸"

    /** Активная кнопка заливается акцентом (primary), неактивная — контурная с цветом текста темы. */
    private fun styleState(btn: MaterialButton, active: Boolean) {
        val primary = themeColor(com.google.android.material.R.attr.colorPrimary, Color.BLUE)
        val onPrimary = themeColor(com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
        val onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val outline = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
        if (active) {
            btn.backgroundTintList = ColorStateList.valueOf(primary)
            btn.setTextColor(onPrimary)
            btn.strokeWidth = 0
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            btn.setTextColor(onSurface)
            btn.strokeWidth = dp(1)
            btn.strokeColor = ColorStateList.valueOf(outline)
        }
    }

    /** Перекрашивает кнопки после смены формы, сетки, привязки и других переключателей. */
    private fun refreshStateButtons() {
        for ((btn, active) in stateButtons) styleState(btn, active())
    }

    /** Поворот телефона: активность не пересоздаётся (configChanges), собираем раскладку заново. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setContentView(buildUi())
        applyForm()
        applyTheme()
        applyFont()
        updatePreviewSwitch()
        // меню собирается заново: в альбоме у «Сохранить» появляется надпись
        invalidateOptionsMenu()
    }

    /** Общие действия: «Сохранить» всегда на виду, остальное в меню «⋯». */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Переключатель приборы/график — левее «Сохранить»: у него меняется надпись, и в правом
        // краю он дёргал бы «Сохранить». У «Сохранить» правый край закреплён, поэтому растёт он влево.
        previewMenuItem = menu.add(Menu.NONE, M_PREVIEW, 0, "Приборы")
        previewMenuItem?.setShowAsAction(
            MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT
        )
        // «Сохранить»: в альбоме надпись, в портрете только иконка - иначе заголовок обрезается
        val save = menu.add(Menu.NONE, M_SAVE, 1, getString(R.string.nav_save))
        save.setIcon(R.drawable.ic_nav_check)
        save.setShowAsAction(
            MenuItem.SHOW_AS_ACTION_ALWAYS or
                (if (isLandscape()) MenuItem.SHOW_AS_ACTION_WITH_TEXT else 0)
        )
        updatePreviewSwitch()
        // Пункты перенесены в аккордеон панели; в меню остаётся только «Сохранить».
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            M_SAVE -> save()
            M_PREVIEW -> togglePreview()
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
        if (::previewFrame.isInitialized) previewFrame.aspect = editor.aspect
        editor.previewGraph = if (previewIndex >= 0) graphs.getOrNull(previewIndex) else null
        updateInfo()
        updatePreviewSwitch()
        refreshStateButtons()
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
        val label = if (previewIndex < 0) {
            "Приборы"
        } else {
            "График: " + (graphs.getOrNull(previewIndex)?.name ?: "график")
        }
        previewMenuItem?.title = label
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

    /** Переключить пропорции предпросмотра: авто (машина) → телефон альбом → телефон портрет. */
    private fun cycleAspects() {
        aspectSource = when (aspectSource) {
            NavAspectSource.AUTO -> NavAspectSource.LANDSCAPE
            NavAspectSource.LANDSCAPE -> NavAspectSource.PORTRAIT
            else -> NavAspectSource.AUTO
        }
        editor.aspect = currentAspect()
        if (::previewFrame.isInitialized) previewFrame.aspect = editor.aspect
        updateInfo()
    }

    private fun aspectLabel(): String = when (aspectSource) {
        NavAspectSource.AUTO ->
            if (NavLayoutStore.carAspect > 0f) "Авто (машина)" else "Авто (телефон)"
        NavAspectSource.CAR -> "Машина"
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
        info?.text = getString(R.string.nav_form) + (if (narrow) getString(R.string.nav_narrow) else getString(R.string.nav_wide)) +
            getString(R.string.nav_aspect_prefix) + aspectLabel() + " " +
            String.format(Locale.US, "%.2f", editor.aspect) +
            getString(R.string.nav_items) + count + "\n" + themeLine() + "\n" + selText
        aspectBtn?.text = "Пресет: " + aspectLabel()
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
        ticksBtn?.text = if (ticksOn) getString(R.string.nav_ticks) + ": вкл" else getString(R.string.nav_ticks) + ": выкл"
        refreshStateButtons()
    }

    private fun toggleDebug() {
        debugOn = !debugOn
        updateInfo()
        debugBtn?.text = if (debugOn) getString(R.string.nav_debug) + ": вкл" else getString(R.string.nav_debug) + ": выкл"
        refreshStateButtons()
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
        applyRegen()
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
