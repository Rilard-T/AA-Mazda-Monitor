package com.aadash.prefs

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

    @Test
    fun apkAssetNameFromReleasePage() {
        // реальный фрагмент страницы releases/expanded_assets/0.9.0
        val flavored = """<a href="/Rilard-T/AA-Mazda-Monitor/releases/download/0.9.0/app-stable-release.apk" """ +
            """rel="nofollow" class="text-bold">app-stable-release.apk</a>""" +
            """<a href="/Rilard-T/AA-Mazda-Monitor/releases/download/0.9.0/mapping.txt">mapping.txt</a>"""
        assertEquals("app-stable-release.apk", UpdateCheck.apkAssetName(flavored, "0.9.0"))

        // старый выпуск без флейвора
        val legacy = "<a href=\"/Rilard-T/AA-Mazda-Monitor/releases/download/0.8.9/app-release.apk\">x</a>"
        assertEquals("app-release.apk", UpdateCheck.apkAssetName(legacy, "0.8.9"))

        // ссылка на другой тег не подходит
        assertNull(UpdateCheck.apkAssetName(legacy, "0.9.0"))
        // APK в выпуске нет — только архив исходников
        assertNull(UpdateCheck.apkAssetName("<a href=\"/x/archive/refs/tags/0.9.0.zip\">z</a>", "0.9.0"))
    }
}
