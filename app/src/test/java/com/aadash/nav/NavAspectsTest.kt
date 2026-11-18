package com.aadash.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class NavAspectsTest {
    private val eps = 0.001f

    @Test
    fun `бессмысленные пропорции отбрасываются`() {
        assertEquals(0f, NavAspects.sanitize(0f), eps)
        assertEquals(0f, NavAspects.sanitize(-2f), eps)
        assertEquals(0f, NavAspects.sanitize(Float.NaN), eps)
        assertEquals(0f, NavAspects.sanitize(0.1f), eps)
        assertEquals(0f, NavAspects.sanitize(12f), eps)
        assertEquals(2.5f, NavAspects.sanitize(2.5f), eps)
    }

    @Test
    fun `пропорции экрана телефона считаются от большей стороны`() {
        assertEquals(2400f / 1080f, NavAspects.landscape(2400, 1080), eps)
        assertEquals(2400f / 1080f, NavAspects.landscape(1080, 2400), eps)
        assertEquals(1080f / 2400f, NavAspects.portrait(2400, 1080), eps)
        assertEquals(0f, NavAspects.landscape(0, 1080), eps)
    }

    @Test
    fun `авто-режим берёт область из машины`() {
        assertEquals(2.1f, NavAspects.of(NavAspectSource.AUTO, 2.1f, 1080, 2400, true), eps)
    }

    @Test
    fun `авто-режим без машины берёт экран телефона по форме`() {
        assertEquals(
            NavAspects.landscape(1080, 2400),
            NavAspects.of(NavAspectSource.AUTO, 0f, 1080, 2400, false), eps
        )
        assertEquals(
            NavAspects.portrait(1080, 2400),
            NavAspects.of(NavAspectSource.AUTO, 0f, 1080, 2400, true), eps
        )
    }

    @Test
    fun `режим машины без данных ничего не придумывает`() {
        assertEquals(0f, NavAspects.of(NavAspectSource.CAR, 0f, 1080, 2400, false), eps)
        assertEquals(0f, NavAspects.of(NavAspectSource.CAR, 99f, 1080, 2400, false), eps)
    }

    @Test
    fun `выбор экрана телефона игнорирует данные машины`() {
        val land = NavAspects.of(NavAspectSource.LANDSCAPE, 2.5f, 1080, 2400, true)
        val port = NavAspects.of(NavAspectSource.PORTRAIT, 2.5f, 1080, 2400, false)
        assertEquals(NavAspects.landscape(1080, 2400), land, eps)
        assertEquals(NavAspects.portrait(1080, 2400), port, eps)
    }

    @Test
    fun `запасная пропорция пригодна`() {
        assertEquals(NavAspects.DEFAULT, NavAspects.sanitize(NavAspects.DEFAULT), eps)
        assertEquals(16f / 9f, NavAspects.DEFAULT, eps)
    }
}
