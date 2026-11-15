package com.aatorque.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun tagToVersionCode() {
        assertEquals(8004, UpdateCheck.versionCodeOf("0.8.4"))
        assertEquals(8004, UpdateCheck.versionCodeOf("v0.8.4"))
        assertEquals(8004, UpdateCheck.versionCodeOf("V0.8.4"))
        assertEquals(8004, UpdateCheck.versionCodeOf("0.8.4-beta"))
        // код версии: minor * 1000, поэтому 0.10.2 — это 10002, а не 10202
        assertEquals(10002, UpdateCheck.versionCodeOf("0.10.2"))
    }

    @Test
    fun unparsableTagGivesNull() {
        assertNull(UpdateCheck.versionCodeOf(null))
        assertNull(UpdateCheck.versionCodeOf(""))
        assertNull(UpdateCheck.versionCodeOf("release-0.9"))
        assertNull(UpdateCheck.versionCodeOf("nightly"))
    }

    @Test
    fun comparisonUsesNumbers() {
        assertTrue(UpdateCheck.isNewer("0.8.4", 8003)!!)
        assertFalse(UpdateCheck.isNewer("0.8.4", 8004)!!)
        assertFalse(UpdateCheck.isNewer("0.7.2", 8004)!!)
        // строковое сравнение здесь ошиблось бы: «0.10.0» < «0.9.0» посимвольно
        assertTrue(UpdateCheck.isNewer("0.10.0", 8004)!!)
        assertNull(UpdateCheck.isNewer("nightly", 8004))
    }

    @Test
    fun versionCodeMatchesBuildFormula() {
        assertEquals(8004, UpdateCheck.versionCode(0, 8, 4))
        assertEquals(8003, UpdateCheck.versionCode(0, 8, 3))
    }
}
