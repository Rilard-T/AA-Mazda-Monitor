package com.aadash.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavCircleSizeTest {
    @Test
    fun `circle scale range allows up to 200 percent`() {
        assertEquals(0.3f, NavLayout.MIN_CIRCLE_SCALE, 0.0001f)
        assertEquals(2.0f, NavLayout.MAX_CIRCLE_SCALE, 0.0001f)
        assertTrue(NavLayout.MAX_CIRCLE_SCALE > 1.0f)
    }

    @Test
    fun `slider progress round-trips 30 to 200 percent without truncation`() {
        val minPct = Math.round(NavLayout.MIN_CIRCLE_SCALE * 100f)
        val maxPct = Math.round(NavLayout.MAX_CIRCLE_SCALE * 100f)
        val barMax = maxPct - minPct
        assertEquals(170, barMax)

        for (pct in listOf(30, 95, 100, 135, 160, 200)) {
            val size = pct / 100f
            val progress = (Math.round(size * 100f) - minPct).coerceIn(0, barMax)
            assertEquals(pct, minPct + progress)
        }
    }

    @Test
    fun `maxCircleSize clamps edge-touching gauge and leaves centered small gauge at 200 percent`() {
        val edgeItem = NavItem("g0", NavItem.GAUGE, "0d", "Speed", "km/h", 0f, 240f, 1.0, 0, 0f, 0f, 1f / 3f, 0.72f, 1f)
        assertEquals(1.0f, NavLayout.maxCircleSize(edgeItem, 2.5f), 0.01f)

        val centeredMid = NavItem("g1", NavItem.GAUGE, "0c", "RPM", "x1000", 0f, 8f, 0.001, 1, 0.35f, 0.1f, 0.3f, 0.4f, 1f)
        assertEquals(1.5f, NavLayout.maxCircleSize(centeredMid, 2.5f), 0.01f)

        val centeredSmall = NavItem("g2", NavItem.GAUGE, "05", "Coolant", "C", 40f, 120f, 1.0, 0, 0.4f, 0.35f, 0.2f, 0.25f, 1f)
        assertEquals(2.0f, NavLayout.maxCircleSize(centeredSmall, 2.5f), 0.001f)
    }

    @Test
    fun `layout presets stay within bounds and have valid items`() {
        for (layout in listOf(NavLayoutStore.defaultLayout(), NavLayoutStore.presetHighway(), NavLayoutStore.presetMinimal())) {
            val wide = layout.form(NavLayoutStore.WIDE).items
            val narrow = layout.form(NavLayoutStore.NARROW).items
            assertTrue(wide.isNotEmpty())
            assertTrue(narrow.isNotEmpty())
            for (item in wide + narrow) {
                assertTrue(item.x >= 0f && item.y >= 0f)
                assertTrue(item.x + item.w <= 1.001f && item.y + item.h <= 1.001f)
            }
        }
    }
}
