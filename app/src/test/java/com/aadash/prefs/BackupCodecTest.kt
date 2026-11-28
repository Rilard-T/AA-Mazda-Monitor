package com.aadash.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    @Test
    fun roundTripKeepsSettingsAndJournal() {
        val settings = byteArrayOf(0, 1, 2, 3, 127, -1)
        val regen = "# journal\nL;1;2.5;;1\nI;300.0\n"

        val text = BackupCodec.encode(settings, regen)
        val back = BackupCodec.decode(text)!!

        assertTrue(settings.contentEquals(back.settings))
        assertEquals(regen, back.regenLog)
        assertEquals("", back.layout)
    }

    @Test
    fun emptyJournalRoundTrips() {
        val back = BackupCodec.decode(BackupCodec.encode(byteArrayOf(1), ""))!!
        assertEquals("", back.regenLog)
    }

    @Test
    fun rejectsForeignFile() {
        assertNull(BackupCodec.decode("just some text\nmore text"))
    }

    @Test
    fun rejectsCorruptBase64() {
        assertNull(BackupCodec.decode("AA_MONITOR_BACKUP;1\n@@@notbase64@@@\nL;1;2;;1"))
    }

    @Test
    fun rejectsEmptyPayload() {
        assertNull(BackupCodec.decode("AA_MONITOR_BACKUP;1\n\nL;1;2;;1"))
    }

    @Test
    fun v2RoundTripKeepsLayout() {
        val settings = byteArrayOf(0, 1, 2, 3, 127, -1)
        val regen = "# journal\nL;1;2.5;;1\n"
        val layout = "{\"gauges\":[1,2,3]}"

        val text = BackupCodec.encode(settings, regen, layout)
        val back = BackupCodec.decode(text)!!

        assertTrue(settings.contentEquals(back.settings))
        assertEquals(regen, back.regenLog)
        assertEquals(layout, back.layout)
        assertTrue(text.startsWith("AA_MONITOR_BACKUP;2"))
    }

    @Test
    fun v1WithoutLayoutDecodesWithEmptyLayout() {
        val settings = byteArrayOf(1, 2, 3)
        val regen = "I;10\n"
        val text = BackupCodec.encode(settings, regen)
        val back = BackupCodec.decode(text)!!
        assertEquals("", back.layout)
    }

    @Test
    fun v2RejectsCorruptLayoutBase64() {
        assertNull(BackupCodec.decode("AA_MONITOR_BACKUP;2\nAQID\n@@@bad@@@\nlog"))
    }
}