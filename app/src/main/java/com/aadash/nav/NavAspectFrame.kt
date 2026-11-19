package com.aadash.nav

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Контейнер предпросмотра с заданной пропорцией (ширина / высота): занимает ровно столько места,
 * сколько нужно раскладке, и вписывается в доступную область без пустых полос вокруг.
 */
class NavAspectFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var aspect: Float = NavAspects.DEFAULT
        set(value) {
            val next = if (value.isFinite() && value > 0f) value else NavAspects.DEFAULT
            if (next != field) {
                field = next
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availW = MeasureSpec.getSize(widthMeasureSpec)
        val availH = MeasureSpec.getSize(heightMeasureSpec)
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        if (availW <= 0 || availH <= 0 || mode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val a = aspect
        var w = availW
        var h = (w / a).toInt()
        if (h > availH) {
            h = availH
            w = (h * a).toInt()
        }
        w = w.coerceAtLeast(1)
        h = h.coerceAtLeast(1)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
        setMeasuredDimension(w, h)
    }
}
