#!/usr/bin/env python3
# Редактор раскладки: кнопка «Назад» (с вопросом при несохранённых изменениях, стрелка в заголовке и системная
# кнопка работают так же), кнопки «Добавить» и «Удалить» для элементов текущей формы.
# Идемпотентный; при несовпадении кода ничего не пишет.
import sys

PATH = "app/src/main/java/com/aatorque/nav/NavEditorActivity.kt"

try:
    src = open(PATH, encoding="utf-8").read()
except OSError as e:
    sys.exit("Не найден %s (запускать из корня репозитория, ветка beta): %s" % (PATH, e))

if "exitEditor" in src:
    print("Уже применено, ничего не меняю.")
    sys.exit(0)

if "copySensorsToAll" not in src:
    sys.exit("В редакторе нет кнопки «Датчики во все формы» (make_nav_menu.py). Сначала примени её. Файл не изменён.")

NEW = r'''package com.aatorque.nav

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
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

    private fun currentLayout(): NavLayout {
        val map = HashMap<String, NavForm>()
        for ((key, list) in forms) map[key] = NavForm(ArrayList(list))
        return NavLayout(narrowAspect, map)
    }

    private fun currentJson(): String = NavLayoutStore.toJson(currentLayout())

    private fun loadFromStore() {
        forms.clear()
        val layout = NavLayoutStore.layout
        for ((key, form) in layout.forms) forms[key] = ArrayList(form.items)
        narrowAspect = layout.narrowAspect
        baseline = currentJson()
    }

    private fun makeButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        return b
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(8), dp(8), dp(8), dp(8))

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.addView(makeButton("Широкая") { formKey = NavLayoutStore.WIDE; presetIdx = 0; applyForm() })
        row1.addView(makeButton("Узкая") { formKey = NavLayoutStore.NARROW; presetIdx = 0; applyForm() })
        row1.addView(makeButton("Пропорции") { presetIdx += 1; applyForm() })
        root.addView(row1)

        editor = NavEditorView(this)
        editor.forms = forms
        editor.onSelectionChanged = { updateInfo() }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        info = TextView(this)
        info.setPadding(dp(4), dp(6), dp(4), dp(6))
        root.addView(info)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.addView(makeButton("Датчик…") { chooseSensor() })
        row2.addView(makeButton("Добавить") { addElement() })
        row2.addView(makeButton("Удалить") { deleteSelected() })
        root.addView(row2)

        val row3 = LinearLayout(this)
        row3.orientation = LinearLayout.HORIZONTAL
        row3.addView(makeButton("Датчики во все формы") { confirmCopySensors() })
        row3.addView(makeButton("Сбросить") { reset() })
        root.addView(row3)

        val row4 = LinearLayout(this)
        row4.orientation = LinearLayout.HORIZONTAL
        row4.addView(makeButton("Назад") { exitEditor() })
        row4.addView(makeButton("Сохранить") { save() })
        root.addView(row4)
        return root
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
            "Коснитесь элемента: тяните, чтобы переместить; кружок в углу меняет размер."
        } else {
            "Выбран: " + sel.label + " (" + sel.pid + ")"
        }
        info.text = "Форма: " + (if (narrow) "узкая" else "широкая") + ", " + names[presetIdx] +
            ", элементов: " + count + "\n" + selText
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
'''

if NEW.count("{") != NEW.count("}"):
    sys.exit("Нарушен баланс скобок в шаблоне. Файл не изменён.")

open(PATH, "w", encoding="utf-8").write(NEW)
print("Готово: «Назад», «Добавить», «Удалить» в редакторе (1 файл).")
