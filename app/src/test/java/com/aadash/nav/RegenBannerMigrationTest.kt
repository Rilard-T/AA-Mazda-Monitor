package com.aadash.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class RegenBannerMigrationTest {
    @Test
    fun `hasX uses x value`() {
        assertEquals(0.5f, RegenBanner.migrateX(true, 0.5, 0), 0.001f)
        assertEquals(1f, RegenBanner.migrateX(true, 2.0, 0), 0.001f)
        assertEquals(0f, RegenBanner.migrateX(true, -1.0, 0), 0.001f)
    }

    @Test
    fun `old corner maps to edges`() {
        assertEquals(0f, RegenBanner.migrateX(false, 0.0, 0), 0.001f)
        assertEquals(0f, RegenBanner.migrateX(false, 0.0, 2), 0.001f)
        assertEquals(1f, RegenBanner.migrateX(false, 0.0, 1), 0.001f)
        assertEquals(1f, RegenBanner.migrateX(false, 0.0, 3), 0.001f)
    }

    @Test
    fun `legacyCorner marks hasX`() {
        assertEquals(-1, RegenBanner.legacyCorner(true, 2))
        assertEquals(2, RegenBanner.legacyCorner(false, 2))
    }
}

class RegenBannerScaleTest {
    @Test
    fun `scaleFor clamps`() {
        assertEquals(0.6f, NavPainter.scaleFor(100f, 100f), 0.001f)
        assertEquals(3f, NavPainter.scaleFor(10000f, 10000f), 0.001f)
        assertEquals(1f, NavPainter.scaleFor(800f, 800f), 0.001f)
    }

    @Test
    fun `height grows with scale`() {
        val low = RegenBannerPainter.height(RegenBanner(scale = 0.6f), 1f)
        val high = RegenBannerPainter.height(RegenBanner(scale = 2f), 1f)
        org.junit.Assert.assertTrue(high > low)
    }
}