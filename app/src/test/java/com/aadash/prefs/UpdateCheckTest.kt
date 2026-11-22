package com.aadash.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun apkAssetNameParsesHtml() {
        val tag = "nav-0.9.0-12"
        val html = "<a href=\"https://github.com/Rilard-T/AA-Mazda-Monitor/releases/download/$tag/AA-Mazda-Monitor-nav.apk\">download</a>"
        assertEquals("AA-Mazda-Monitor-nav.apk", UpdateCheck.apkAssetName(html, tag))
    }

    @Test
    fun apkAssetNameReturnsNullWhenNoApk() {
        assertNull(UpdateCheck.apkAssetName("<html>no apk</html>", "nav-0.9.0-12"))
    }

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

    @Test
    fun navTagsCompareVersionThenBuild() {
        assertEquals(9000 to 12, UpdateCheck.navTagParams("nav-0.9.0-12"))
        assertNull(UpdateCheck.navTagParams("0.9.0"))
        assertTrue(UpdateCheck.isNewerNav("nav-0.9.0-13", 9000, 12)!!)
        assertFalse(UpdateCheck.isNewerNav("nav-0.9.0-12", 9000, 12)!!)
        assertTrue(UpdateCheck.isNewerNav("nav-0.9.1-1", 9000, 40)!!)
        assertFalse(UpdateCheck.isNewerNav("nav-0.8.9-40", 9000, 1)!!)
        assertEquals(12, UpdateCheck.navBuildOf("0.9.0-nav.12"))
        assertEquals(0, UpdateCheck.navBuildOf("0.9.0"))
    }

    @Test
    fun newestNavTagFromAtomFeed() {
        val feed = """<?xml version="1.0"?><feed>
            <entry><title>nav-0.8.9-9</title></entry>
            <entry><title>nav-0.9.0-12</title></entry>
            <entry><title>0.9.0</title></entry>
            <entry><title>nav-0.8.10-57</title></entry>
        </feed>"""
        assertEquals("nav-0.9.0-12", UpdateCheck.newestNavTag(feed))
        assertNull(UpdateCheck.newestNavTag("<feed><title>0.9.0</title></feed>"))
        assertNull(UpdateCheck.newestNavTag(null))
    }
}
