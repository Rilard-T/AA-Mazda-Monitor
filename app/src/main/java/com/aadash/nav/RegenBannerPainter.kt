package com.aadash.nav

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Уведомление о прожиге: одна и та же геометрия для шапки на экране машины (там, где «Выход»
 * и кнопки экранов) и для предпросмотра в редакторе раскладки, иначе подобранный в редакторе
 * сдвиг и размер не совпадут. Ширина — по надписи, по вертикали плашка стоит по центру полосы шапки.
 * Все размеры заданы в базовых dp, а [dp] — сколько пикселей приходится на такую единицу:
 * на экране машины это масштаб её области, в предпросмотре — масштаб самого полотна приборов,
 * поэтому предпросмотр выглядит как тот же экран машины, уменьшенный целиком.
 */
object RegenBannerPainter {
    private const val BASE_HEIGHT_DP = 28f
    private const val MARGIN_DP = 8f
    private const val PADDING_DP = 9f
    private const val RADIUS_DP = 8f
    private const val BORDER_DP = 1.5f
    private const val TEXT_DP = 13f

    private fun scaleOf(banner: RegenBanner): Float =
        banner.scale.coerceIn(RegenBanner.MIN_SCALE, RegenBanner.MAX_SCALE)

    /** Высота плашки: базовая с масштабом, но не меньше самого текста. */
    fun height(banner: RegenBanner, dp: Float): Float {
        val s = scaleOf(banner)
        return maxOf(BASE_HEIGHT_DP * s * dp, TEXT_DP * s * dp * 1.6f)
    }

    private fun textSizeAt(banner: RegenBanner, dp: Float): Float = TEXT_DP * scaleOf(banner) * dp

    private fun paddingAt(banner: RegenBanner, dp: Float): Float = PADDING_DP * scaleOf(banner) * dp

    private fun naturalWidth(banner: RegenBanner, dp: Float, label: String, text: Paint): Float {
        text.textSize = textSizeAt(banner, dp)
        return text.measureText(label) + 2f * paddingAt(banner, dp)
    }

    /**
     * Прямоугольник плашки внутри полосы [band]: по горизонтали — сдвиг [RegenBanner.x]
     * (0 — у левого края полосы, 1 — у правого), по вертикали — по центру полосы.
     * Кегль в [text] выставляется только для замера ширины надписи.
     */
    fun bounds(band: RectF, banner: RegenBanner, dp: Float, label: String, text: Paint, out: RectF) {
        val w = naturalWidth(banner, dp, label, text)
        val h = height(banner, dp)
        val m = MARGIN_DP * dp
        val span = (band.width() - w - 2f * m).coerceAtLeast(0f)
        val left = band.left + m + banner.x.coerceIn(0f, 1f) * span
        val top = if (band.height() >= h) band.top + (band.height() - h) / 2f else band.top
        out.set(left, top, left + w, top + h)
    }

    /** Сдвиг для перетаскивания пальцем: положение левого края плашки по координате касания. */
    fun xFor(band: RectF, banner: RegenBanner, dp: Float, label: String, text: Paint, touchX: Float): Float {
        val w = naturalWidth(banner, dp, label, text)
        val m = MARGIN_DP * dp
        val span = band.width() - w - 2f * m
        if (span <= 0f) return 0f
        return ((touchX - w / 2f - band.left - m) / span).coerceIn(0f, 1f)
    }

    /**
     * Рисует плашку в готовом прямоугольнике (его считает [bounds]). Кегль, отступ и обводку
     * берёт из [dp], а не из размеров прямоугольника, поэтому [draw] не зависит от того,
     * что [bounds] уже трогал перо надписи.
     */
    fun draw(
        canvas: Canvas,
        rect: RectF,
        banner: RegenBanner,
        dp: Float,
        label: String,
        fill: Paint,
        border: Paint,
        text: Paint
    ) {
        val s = scaleOf(banner)
        val radius = RADIUS_DP * s * dp
        fill.color = banner.fill
        canvas.drawRoundRect(rect, radius, radius, fill)
        border.style = Paint.Style.STROKE
        border.strokeWidth = BORDER_DP * s * dp
        border.color = banner.border
        canvas.drawRoundRect(rect, radius, radius, border)
        text.color = banner.text
        text.textAlign = Paint.Align.LEFT
        text.textSize = textSizeAt(banner, dp)
        val baseline = rect.centerY() - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, rect.left + paddingAt(banner, dp), baseline, text)
    }
}
