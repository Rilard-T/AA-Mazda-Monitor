package com.aadash.prefs

import java.util.Base64

/**
 * Единый файл резервной копии: настройки (protobuf) и журнал прожигов в одном файле.
 *
 * Формат: первая строка — заголовок с версией, вторая — base64 настроек, дальше —
 * текстовый журнал прожигов (формат [com.aadash.obd.RegenLogParser]). Чистые функции,
 * поэтому проверяются юнит-тестами на JVM.
 */
object BackupCodec {

    private const val HEADER = "AA_MONITOR_BACKUP;1"

    data class Backup(val settings: ByteArray, val regenLog: String)

    fun encode(settings: ByteArray, regenLog: String): String =
        HEADER + "\n" + Base64.getEncoder().encodeToString(settings) + "\n" + regenLog

    /** null — файл не является резервной копией или повреждён. */
    fun decode(text: String): Backup? {
        val first = text.indexOf('\n')
        if (first < 0) return null
        if (!text.substring(0, first).trim().equals(HEADER, ignoreCase = false)) return null
        val second = text.indexOf('\n', first + 1)
        if (second < 0) return null
        val settings = runCatching {
            Base64.getDecoder().decode(text.substring(first + 1, second).trim())
        }.getOrNull() ?: return null
        if (settings.isEmpty()) return null
        return Backup(settings, text.substring(second + 1))
    }
}
