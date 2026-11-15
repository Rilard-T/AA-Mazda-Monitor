package com.aatorque.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Формулы датчиков: те же функции, что использует приложение для приборов и журнала прожигов. */
class MazdaDecodeTest {

    @Test
    fun sootAccumulationAndGeneration() {
        // 0x8000 из 65535 — половина шкалы, 50 г/л при диапазоне 0..100
        assertEquals(50.0, MazdaDecode.soot(intArrayOf(0x80, 0x00)), 0.001)
        assertEquals(0.0, MazdaDecode.soot(intArrayOf(0x00, 0x00)), 0.001)
        // один и тот же байтовый ответ для 22 042C и 22 042D читается одинаково
        assertEquals(MazdaDecode.soot(intArrayOf(0x02, 0x00)), MazdaDecode.soot(intArrayOf(0x02, 0x00)), 0.0)
    }

    @Test
    fun regenCounterAndDistance() {
        assertEquals(258.0, MazdaDecode.regenCounter(intArrayOf(0x01, 0x02)), 0.0)
        // три байта 0A 00 00 из 0x10000 — 1000 км
        assertEquals(1000.0, MazdaDecode.kmSinceRegen(intArrayOf(0x00, 0x0A, 0x00, 0x00)), 0.001)
        assertEquals(0.0, MazdaDecode.kmSinceRegen(intArrayOf(0x00, 0x00, 0x00, 0x00)), 0.001)
    }

    @Test
    fun transmissionOilTemperatureIsSigned() {
        assertEquals(192.0, MazdaDecode.transTemp(intArrayOf(0x0C, 0x00)), 0.001)
        assertEquals(-16.0, MazdaDecode.transTemp(intArrayOf(0xFF, 0x00)), 0.001)
    }

    @Test
    fun standardSensors() {
        assertEquals(1726.0, MazdaDecode.rpm(intArrayOf(0x1A, 0xF8)), 0.001)
        assertEquals(83.0, MazdaDecode.at(123), 0.001)
        assertEquals(100.0, MazdaDecode.percent(255), 0.001)
        assertEquals(40.0, MazdaDecode.maf(intArrayOf(0x0F, 0xA0)), 0.001)
        assertEquals(14.5, MazdaDecode.voltage(intArrayOf(0x38, 0xA4)), 0.001)
        assertEquals(120.0, MazdaDecode.railPressure(intArrayOf(0x2E, 0xE0)), 0.001)
    }

    @Test
    fun dpfPressureRespectsDataFlag() {
        assertEquals(1.0, MazdaDecode.dpfPressure(intArrayOf(0x01, 0x00, 0x64))!!, 0.001)
        assertEquals(-1.0, MazdaDecode.dpfPressure(intArrayOf(0x01, 0xFF, 0x9C))!!, 0.001)
        assertNull(MazdaDecode.dpfPressure(intArrayOf(0x00, 0x00, 0x64)))
    }

    @Test
    fun boostIsRelativeToBarometricPressure() {
        // 0x1F40 = 8000 * 0.03125 = 250 кПа при атмосфере 101 кПа
        assertEquals(1.49, MazdaDecode.boost(intArrayOf(0x02, 0x00, 0x00, 0x1F, 0x40), 101.0)!!, 0.001)
        assertNull(MazdaDecode.boost(intArrayOf(0x00, 0x00, 0x00, 0x1F, 0x40), 101.0))
    }

    @Test
    fun regenStatusIsRawByte() {
        assertEquals(0.0, MazdaDecode.regenStatus(intArrayOf(0x00)), 0.0)
        assertEquals(2.0, MazdaDecode.regenStatus(intArrayOf(0x02, 0x00)), 0.0)
    }
}
