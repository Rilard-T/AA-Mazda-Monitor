package com.aatorque.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Пользовательские PID: формулы Torque, импорт CSV и формат хранения списка. */
class UserPidFormulaTest {

    @Test
    fun variablesAreResponseBytes() {
        val v = UserPidFormula.evaluate("A*256+B", intArrayOf(0x04, 0x34))!!
        assertEquals(0x0434.toDouble(), v, 0.0001)
    }

    @Test
    fun missingBytesAreZeros() {
        assertEquals(5.0, UserPidFormula.evaluate("A+E", intArrayOf(5))!!, 0.0001)
    }

    @Test
    fun signedIsSixteenBit() {
        // 0xFFEC = −20 в 16-битном виде: температура АКПП не должна стать 65516/16.
        assertEquals(-1.25, UserPidFormula.evaluate("SIGNED(A*256+B)/16", intArrayOf(0xFF, 0xEC))!!, 0.0001)
        // 0x7F00 — положительное значение, знак не должен теряться.
        assertEquals(32512.0, UserPidFormula.evaluate("SIGNED(A*256+B)", intArrayOf(0x7F, 0x00))!!, 0.0001)
    }

    @Test
    fun torqueFunctionsWork() {
        assertEquals(1.0, UserPidFormula.evaluate("BIT(A,0)", intArrayOf(0x05))!!, 0.0001)
        assertEquals(0.0, UserPidFormula.evaluate("BIT(A,1)", intArrayOf(0x05))!!, 0.0001)
        assertEquals(2.0, UserPidFormula.evaluate("INT(A/2)", intArrayOf(0x05))!!, 0.0001)
        assertEquals(90.0, UserPidFormula.evaluate("MAX(A,B)+MIN(A,B)", intArrayOf(60, 30))!!, 0.0001)
    }

    @Test
    fun brokenFormulaIsRejected() {
        assertNull(UserPidFormula.validate("A*256+B"))
        assertNotNull(UserPidFormula.validate("A*"))
        assertNull(UserPidFormula.evaluate("A*", intArrayOf(1)))
    }

    @Test
    fun torqueCsvIsParsed() {
        val csv = """
            Name,ShortName,ModeAndPID,Equation,Min Value,Max Value,Units,Header
            "Oil temp, engine",Oil,221E1C,SIGNED(A*256+B)/16,-40,150,°C,7E1
            Speed,Spd,010D,A,0,255,km/h,
        """.trimIndent()
        val pids = UserPidCsv.parse(csv)
        assertEquals(2, pids.size)
        assertEquals("Oil temp, engine", pids[0].name)
        assertEquals("221E1C", pids[0].modeAndPid)
        assertEquals("7E1", pids[0].header)
        assertEquals(-40.0, pids[0].min, 0.0001)
        // без заголовка блок считается двигателем
        assertEquals("7E0", pids[1].header)
        assertEquals("u221e1c", pids[0].key)
    }

    @Test
    fun semicolonCsvIsParsed() {
        val pids = UserPidCsv.parse("Boost;Boost;220434;(A*256+B)*0.01;0;2;bar;7E0")
        assertEquals(1, pids.size)
        assertEquals(2.0, pids[0].max, 0.0001)
        assertEquals("bar", pids[0].unit)
    }

    @Test
    fun rowsWithoutFormulaAreSkipped() {
        assertTrue(UserPidCsv.parse("Speed,Spd,010D,,0,255,km/h,7E0").isEmpty())
        assertTrue(UserPidCsv.parse("Speed,Spd,01,0,255,km/h,7E0").isEmpty())
    }

    @Test
    fun pidAndHeaderAreNormalized() {
        assertEquals("22042C", UserPidCodec.normalizePid("22 04 2c"))
        assertNull(UserPidCodec.normalizePid("22 04 2"))
        assertEquals("7E0", UserPidCodec.normalizeHeader(""))
        assertEquals("7E1", UserPidCodec.normalizeHeader("7e1"))
    }

    @Test
    fun storedListSurvivesSpecialCharacters() {
        val pid = UserPid(
            name = "Oil\ttemp", shortName = "Oil", modeAndPid = "221E1C",
            equation = "SIGNED(A*256+B)/16", min = -40.0, max = 150.0, unit = "°C", header = "7E1",
        )
        assertEquals(listOf(pid), UserPidCodec.decode(UserPidCodec.encode(listOf(pid))))
    }
}
