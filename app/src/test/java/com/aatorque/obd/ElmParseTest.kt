package com.aatorque.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Разбор ответов адаптера: однострочные и многострочные (ELM327 с номерами кадров). */
class ElmParseTest {

    @Test
    fun singleFrameAfterPrefix() {
        assertEquals(listOf(0x1A, 0xF8), ElmParse.bytes("410C1AF8", "410C")!!.toList())
    }

    @Test
    fun spacesAndLowercaseAreAccepted() {
        assertEquals(listOf(0x1A, 0xF8), ElmParse.bytes("41 0c 1a f8", "410C")!!.toList())
    }

    @Test
    fun multiFrameCassetteNumbersAreStripped() {
        val bytes = ElmParse.bytes("0: 62 04 34 00 0A\n1: 00 00 00 00", "620434")
        assertEquals(listOf(0, 10, 0, 0, 0, 0), bytes!!.toList())
    }

    @Test
    fun noResponseGivesNull() {
        assertNull(ElmParse.bytes("NO DATA", "410C"))
        assertNull(ElmParse.bytes(null, "410C"))
        assertNull(ElmParse.bytes("", "410C"))
    }

    @Test
    fun prefixWithoutPayloadGivesEmptyArray() {
        assertEquals(0, ElmParse.bytes("410C", "410C")!!.size)
    }

    @Test
    fun egtTemperaturesSkipMissingSensors() {
        val b = intArrayOf(0x07, 0x00, 0x00, 0x0F, 0x0A, 0x0F, 0x3C, 0x0F, 0x00)
        assertEquals(listOf(345.0, 350.0, 344.0), ElmParse.egtTemperatures(b))
    }

    @Test
    fun singleFrameWithBlockHeaderIsParsed() {
        // Адаптер не убрал заголовок блока и служебный байт ISO-TP (03 — длина полезных данных).
        assertEquals(listOf(0x32), ElmParse.bytes("7E8 03 41 0D 32", "410D")!!.toList())
    }

    @Test
    fun rawIsoTpFramesAreAssembled() {
        // Многосегментный ответ, разрезанный кадрами: префикс straddle-ит границу кадров,
        // поэтому прямой поиск не находит его и работает резервный сборщик ISO-TP.
        val raw = "7E8 10 0A 62 1E\n7E8 21 1C 12 34"
        assertEquals(listOf(0x12, 0x34), ElmParse.bytes(raw, "621E1C")!!.toList())
    }

    @Test
    fun rawFramesWithoutAnswerGivesNull() {
        assertNull(ElmParse.bytes("7E8 03 41 0D 32", "620434"))
    }
}
