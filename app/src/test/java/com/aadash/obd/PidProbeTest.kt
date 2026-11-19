package com.aadash.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Учёт ответов блока: датчик без ответа помечается и снимается с пометки при ответе. */
class PidProbeTest {

    @Test
    fun marksMissingAndClearsOnAnswer() {
        PidProbe.reset()
        assertFalse(PidProbe.isMissing("ff9015"))
        PidProbe.onAnswer("FF9015", false)
        assertTrue(PidProbe.isMissing("ff9015"))
        PidProbe.onAnswer("ff9015", true)
        assertFalse(PidProbe.isMissing("ff9015"))
        assertFalse(PidProbe.done)
        PidProbe.finish()
        assertTrue(PidProbe.done)
        PidProbe.reset()
        assertFalse(PidProbe.done)
    }
}
