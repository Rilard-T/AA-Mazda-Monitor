package com.aatorque.nav

/** Откуда брать пропорции области для предпросмотра раскладки. */
enum class NavAspectSource { AUTO, CAR, LANDSCAPE, PORTRAIT }

/**
 * Пропорции предпросмотра (ширина / высота) без зашитых размеров конкретных магнитол:
 * реальная область, которую сообщил Android Auto, а если её ещё не было - экран телефона.
 * Все методы чистые: 0 означает «данных нет».
 */
object NavAspects {
    /** Запасная пропорция, когда нет ни машины, ни размеров экрана (16:9). */
    const val DEFAULT = 16f / 9f

    private const val MIN = 0.35f
    private const val MAX = 6f

    /** Пригодная пропорция или 0, если значение бессмысленное (ноль, отрицательное, NaN, крайность). */
    fun sanitize(aspect: Float): Float =
        if (aspect.isFinite() && aspect >= MIN && aspect <= MAX) aspect else 0f

    /** Пропорция экрана телефона в альбомной ориентации (большая сторона к меньшей). */
    fun landscape(w: Int, h: Int): Float {
        if (w <= 0 || h <= 0) return 0f
        return sanitize(maxOf(w, h).toFloat() / minOf(w, h).toFloat())
    }

    /** Пропорция экрана телефона в портретной ориентации. */
    fun portrait(w: Int, h: Int): Float {
        val l = landscape(w, h)
        return if (l > 0f) sanitize(1f / l) else 0f
    }

    /**
     * Пропорция области для предпросмотра.
     * @param source откуда брать: авто/машина/экран телефона в альбоме/портрете
     * @param car последняя область из Android Auto (0 - ещё не запускались в машине)
     * @param w ширина экрана телефона
     * @param h высота экрана телефона
     * @param narrowForm текущая форма узкая (для авто-режима без данных машины берётся портрет)
     * @return пропорция или 0, если взять неоткуда
     */
    fun of(source: NavAspectSource, car: Float, w: Int, h: Int, narrowForm: Boolean): Float {
        val carA = sanitize(car)
        return when (source) {
            NavAspectSource.CAR -> carA
            NavAspectSource.LANDSCAPE -> landscape(w, h)
            NavAspectSource.PORTRAIT -> portrait(w, h)
            NavAspectSource.AUTO -> if (carA > 0f) carA else if (narrowForm) portrait(w, h) else landscape(w, h)
        }
    }
}
