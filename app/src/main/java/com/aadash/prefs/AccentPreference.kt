package com.aadash.prefs

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.aadash.stats.R
import com.rarepebble.colorpicker.ColorPickerView

/**
 * Акцентный цвет: любой оттенок через HSV-палитру (в приложении уже есть hsv-alpha-color-picker).
 *
 * Значение хранит не список пресетов, а сам цвет; палитру Material 3 из него строит [AppAccent].
 * После выбора экран пересоздаётся — иначе уже созданные элементы остаются в прежнем цвете.
 */
class AccentPreference(context: Context, attributeSet: AttributeSet) : Preference(context, attributeSet) {

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val picker = ColorPickerView(context).apply {
            showHex(false)
            color = AppAccent.seed(context)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(picker)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                apply(picker.color)
                dialog.dismiss()
            }
            .setNeutralButton(R.string.accent_reset) { dialog, _ ->
                apply(AppAccent.seedForPreset(context, AppAccent.DEFAULT_PRESET))
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun apply(@ColorInt seed: Int) {
        AppAccent.save(context, seed)
        refresh()
        (context as? Activity)?.recreate()
    }

    private fun refresh() {
        val seed = AppAccent.seed(context)
        summary = String.format("#%06X", 0xFFFFFF and seed)
        setIcon(swatch(seed))
    }

    /** Кружок выбранного цвета перед названием пункта. */
    private fun swatch(@ColorInt color: Int): Drawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize((24 * density).toInt(), (24 * density).toInt())
            setStroke((density).toInt(), 0x33000000)
        }
    }
}
