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
    }

    @Test
    fun roundTripKeepsNavLayout() {
        val settings = byteArrayOf(1, 2, 3)
        val regen = "L;1;2.5;;1\n"
        val layout = "{\"v\":2,\"forms\":{}}"

        val text = BackupCodec.encode(settings, regen, layout)
        val back = BackupCodec.decode(text)!!

        assertTrue(settings.contentEquals(back.settings))
        assertEquals(regen, back.regenLog)
        assertEquals(layout, back.navLayout)
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
}
