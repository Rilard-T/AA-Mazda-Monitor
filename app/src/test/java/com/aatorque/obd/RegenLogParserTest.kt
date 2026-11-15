package com.aatorque.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegenLogParserTest {

    @Test
    fun parseEmptyText() {
        val parsed = RegenLogParser.parse("")
        assertTrue(parsed.entries.isEmpty())
        assertTrue(parsed.intervals.isEmpty())
    }

    @Test
    fun parseIgnoresCommentsAndBlankLines() {
        val text = """
            # header
            # L;time;soot;km;state

            I;100.0
        """.trimIndent()
        val parsed = RegenLogParser.parse(text)
        assertEquals(0, parsed.entries.size)
        assertEquals(1, parsed.intervals.size)
        assertEquals(100.0, parsed.intervals[0], 0.001)
    }

    @Test
    fun parseValidEntry() {
        val text = "L;1000;5.5;123.4;1"
        val parsed = RegenLogParser.parse(text)
        assertEquals(1, parsed.entries.size)
        assertEquals(1000L, parsed.entries[0].time)
        assertEquals(5.5, parsed.entries[0].soot, 0.001)
        assertEquals(123.4, parsed.entries[0].km!!, 0.001)
        assertEquals(RegenState.COMPLETE, parsed.entries[0].state)
    }

    @Test
    fun parseSkipsInvalidEntries() {
        val text = """
            L;0;5.0;;1
            L;1000;abc;;1
            L;1000;5.0;;5
            L;1000;35.0;;1
            L;1000;5.0;;1
        """.trimIndent()
        val parsed = RegenLogParser.parse(text)
        assertEquals(1, parsed.entries.size)
    }

    @Test
    fun parseEntryWithoutKm() {
        val text = "L;2000;3.0;;3"
        val parsed = RegenLogParser.parse(text)
        assertEquals(1, parsed.entries.size)
        assertEquals(null, parsed.entries[0].km)
        assertEquals(RegenState.BY_COUNTER, parsed.entries[0].state)
    }

    @Test
    fun parseInterval() {
        val text = "I;50.5\nI;-10\nI;0"
        val parsed = RegenLogParser.parse(text)
        assertEquals(1, parsed.intervals.size)
        assertEquals(50.5, parsed.intervals[0], 0.001)
    }

    @Test
    fun formatRoundTrip() {
        val entries = listOf(
            RegenEntry(1000L, 5.5, 123.4, RegenState.COMPLETE),
            RegenEntry(2000L, 3.0, null, RegenState.BY_COUNTER)
        )
        val intervals = listOf(100.0, 200.0)
        val text = RegenLogParser.format(entries, intervals)
        val parsed = RegenLogParser.parse(text)
        assertEquals(2, parsed.entries.size)
        assertEquals(2, parsed.intervals.size)
        assertEquals(1000L, parsed.entries[0].time)
        assertEquals(5.5, parsed.entries[0].soot, 0.001)
        assertEquals(100.0, parsed.intervals[0], 0.001)
    }

    @Test
    fun parseDefaultState() {
        val text = "L;3000;4.0;50.0"
        val parsed = RegenLogParser.parse(text)
        assertEquals(1, parsed.entries.size)
        assertEquals(RegenState.BY_COUNTER, parsed.entries[0].state)
    }
}
