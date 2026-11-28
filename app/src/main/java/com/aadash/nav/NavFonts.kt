package com.aadash.nav

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.aadash.stats.R

object NavFonts {
    private val cache = HashMap<Int, Typeface>()

    fun load(context: Context, name: String): Typeface? {
        val resId = when (name) {
            "segments" -> R.font.digital
            "seat" -> R.font.seat_metastyle_monodigit_regular
            "audi" -> R.font.auditypedisplayhigh
            "vw" -> R.font.vwtextcarui_regular
            "vw2" -> R.font.vwthesis_mib_regular
            "frutiger" -> R.font.frutiger
            "vw3" -> R.font.vw_digit_reg
            "skoda" -> R.font.skoda
            "larabie" -> R.font.larabie
            "ford" -> R.font.unitedsans
            "ev" -> R.font.ev
            else -> R.font.digital
        }
        synchronized(cache) {
            cache[resId]?.let { return it }
        }
        return try {
            val tf = ResourcesCompat.getFont(context.applicationContext, resId)
            if (tf != null) synchronized(cache) { cache[resId] = tf }
            tf
        } catch (e: Exception) {
            null
        }
    }
}
