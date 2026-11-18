package com.aadash.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Шаг подписей шкал и выбор формы раскладки — без Android, на JVM. */
class GaugeScaleTest {

    @Test
    fun rpmUsesWholeSteps() {
        val (step, n) = GaugeScale.ticks(8f, 0f)
        assertEquals(1f, step, 1e-4f)
        assertEquals(9, n)
    }

    @Test
    fun coolantKeepsRoundMinimum() {
        val (step, n) = GaugeScale.ticks(80f, 40f)
        assertEquals(10f, step, 1e-4f)
        assertEquals(9, n)
    }

    @Test
    fun speedTwoHundredUsesTwentyFive() {
        val (step, n) = GaugeScale.ticks(200f, 0f)
        assertEquals(25f, step, 1e-4f)
        assertEquals(9, n)
    }

    @Test
    fun speedTwoFortyFallsBackToEightParts() {
        val (step, n) = GaugeScale.ticks(240f, 0f)
        assertEquals(30f, step, 1e-4f)
        assertEquals(9, n)
    }

    @Test
    fun ninetyDegreesIsTenSteps() {
        val (step, n) = GaugeScale.ticks(90f, 0f)
        assertEquals(10f, step, 1e-4f)
        assertEquals(10, n)
    }

    @Test
    fun emptyRangeIsSafe() {
        val (step, n) = GaugeScale.ticks(0f, 0f)
        assertEquals(1f, step, 1e-4f)
        assertEquals(2, n)
    }

    @Test
    fun missingFormFallsBackToWide() {
        val wide = NavForm(emptyList())
        val layout = NavLayout(0.9f, mapOf(NavLayoutStore.WIDE to wide))
        assertSame(wide, layout.form(NavLayoutStore.NARROW))
        assertEquals(false, layout.debug)
    }
}
