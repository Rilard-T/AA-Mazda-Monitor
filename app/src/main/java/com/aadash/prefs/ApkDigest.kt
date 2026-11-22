package com.aadash.prefs

import java.io.InputStream
import java.security.MessageDigest

/**
 * Проверка целостности скачанного APK. Рядом с APK в релизе лежит файл
 * `<имя>.apk.sha256` с хешем; если его нет (старые выпуски), проверка пропускается.
 */
object ApkDigest {

    private const val HEX = "0123456789abcdef"

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xff
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return out.toString()
    }

    fun sha256(input: InputStream): String = input.use { sha256(it.readBytes()) }

    /**
     * Хеш из файла вида `974e5d…  app-release.apk` или просто `974e5d…`.
     * null — строка не похожа на SHA-256.
     */
    fun parse(text: String): String? {
        val token = text.trim().substringBefore(' ').substringBefore('\t').trim().lowercase()
        return token.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }

    fun matches(expected: String?, actual: String): Boolean =
        expected == null || expected.equals(actual, ignoreCase = true)
}
