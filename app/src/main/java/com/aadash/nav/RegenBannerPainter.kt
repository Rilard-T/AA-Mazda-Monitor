package com.aadash.nav

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Уведомление о прожиге: одна и та же геометрия для приборной панели на экране машины и для
 * предпросмотра в редакторе раскладки, иначе подобранные в редакторе размер и место не совпадут.
 */
object RegenBannerPainter {
    private const val BASE_WIDTH_DP = 150f
    private const val BASE_HEIGHT_DP = 28f
    private const val MARGIN_DP = 10f
    private const val PADDING_DP = 8f
    private const val RADIUS_DP = 8f
    private const val BORDER_DP = 1.5f
    private const val TEXT_DP = 13f

    /** Прямоугольник баннера в координатах области; [dp] — сколько пикселей в одном dp. */
    fun bounds(area: RectF, banner: RegenBanner, dp: Float, out: RectF) {
        val w = BASE_WIDTH_DP * banner.scale * dp
        val h = BASE_HEIGHT_DP * banner.scale * dp
        val m = MARGIN_DP * dp
        val right = banner.corner == RegenBanner.TOP_RIGHT || banner.corner == RegenBanner.BOTTOM_RIGHT
        val bottom = banner.corner == RegenBanner.BOTTOM_LEFT || banner.corner == RegenBanner.BOTTOM_RIGHT
        val left = if (right) area.right - m - w else area.left + m
        val top = if (bottom) area.bottom - m - h else area.top + m
        out.set(left, top, left + w, top + h)
    }

    /** Рисует плашку с текстом. Кисти передаются готовыми: цвета и стиль задаются здесь. */
    fun draw(
        canvas: Canvas,
        area: RectF,
        banner: RegenBanner,
        dp: Float,
        label: String,
        fill: Paint,
        border: Paint,
        text: Paint,
        out: RectF = RectF()
    ) {
        bounds(area, banner, dp, out)
        val radius = RADIUS_DP * banner.scale * dp
        fill.color = banner.fill
        canvas.drawRoundRect(out, radius, radius, fill)
        border.style = Paint.Style.STROKE
        border.strokeWidth = BORDER_DP * banner.scale * dp
        border.color = banner.border
        canvas.drawRoundRect(out, radius, radius, border)
        text.color = banner.text
        text.textSize = TEXT_DP * banner.scale * dp
        text.textAlign = Paint.Align.LEFT
        canvas.drawText(label, out.left + PADDING_DP * banner.scale * dp, out.top + out.height() * 0.68f, text)
    }
}
