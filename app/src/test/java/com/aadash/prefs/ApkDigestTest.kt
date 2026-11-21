package com.aadash.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkDigestTest {

    private val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val abcSha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun hashesBytes() {
        assertEquals(abcSha, ApkDigest.sha256("abc".toByteArray()))
    }

    @Test
    fun hashesEmpty() {
        assertEquals(emptySha, ApkDigest.sha256(ByteArray(0)))
    }

    @Test
    fun parsesSha256sumLine() {
        assertEquals(abcSha, ApkDigest.parse("$abcSha  app-release.apk\n"))
    }

    @Test
    fun parsesBareHash() {
        assertEquals(emptySha, ApkDigest.parse("$emptySha\n"))
    }

    @Test
    fun rejectsShortOrInvalid() {
        assertNull(ApkDigest.parse("not a hash"))
        assertNull(ApkDigest.parse("zzzz" + emptySha.drop(4)))
    }

    @Test
    fun matchesIgnoresCaseAndMissingExpected() {
        assertTrue(ApkDigest.matches(abcSha.uppercase(), abcSha))
        assertTrue(ApkDigest.matches(null, abcSha))
        assertFalse(ApkDigest.matches(emptySha, abcSha))
    }
}
