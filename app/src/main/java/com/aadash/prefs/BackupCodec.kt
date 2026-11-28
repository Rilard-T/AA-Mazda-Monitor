package com.aadash.prefs

import java.util.Base64

/**
 * Единый файл резервной копии: настройки (protobuf), раскладка приборов и журнал прожигов.
 *
 * Формат версии 1 (обратная совместимость):
 *   AA_MONITOR_BACKUP;1
 *   <base64 settings>
 *   <regenLog>
 *
 * Формат версии 2 (с раскладкой):
 *   AA_MONITOR_BACKUP;2
 *   <base64 settings>
 *   <base64 layoutJson>
 *   <regenLog>
 *
 * Чистые функции, поэтому проверяются юнит-тестами на JVM.
 */
object BackupCodec {

    private const val HEADER_1 = "AA_MONITOR_BACKUP;1"
    private const val HEADER_2 = "AA_MONITOR_BACKUP;2"

    data class Backup(val settings: ByteArray, val regenLog: String, val layout: String = "")

    fun encode(settings: ByteArray, regenLog: String, layout: String = ""): String {
        return if (layout.isBlank()) {
            HEADER_1 + "\n" + Base64.getEncoder().encodeToString(settings) + "\n" + regenLog
        } else {
            HEADER_2 + "\n" + Base64.getEncoder().encodeToString(settings) + "\n" +
                Base64.getEncoder().encodeToString(layout.toByteArray(Charsets.UTF_8)) + "\n" + regenLog
        }
    }

    /** null — файл не является резервной копией или повреждён. */
    fun decode(text: String): Backup? {
        val first = text.indexOf('\n')
        if (first < 0) return null
        val header = text.substring(0, first).trim()
        return when (header) {
            HEADER_1 -> decodeV1(text, first)
            HEADER_2 -> decodeV2(text, first)
            else -> null
        }
    }

    private fun decodeV1(text: String, first: Int): Backup? {
        val second = text.indexOf('\n', first + 1)
        if (second < 0) return null
        val settings = runCatching {
            Base64.getDecoder().decode(text.substring(first + 1, second).trim())
        }.getOrNull() ?: return null
        if (settings.isEmpty()) return null
        return Backup(settings, text.substring(second + 1), "")
    }

    private fun decodeV2(text: String, first: Int): Backup? {
        val second = text.indexOf('\n', first + 1)
        if (second < 0) return null
        val third = text.indexOf('\n', second + 1)
        if (third < 0) return null
        val settings = runCatching {
            Base64.getDecoder().decode(text.substring(first + 1, second).trim())
        }.getOrNull() ?: return null
        if (settings.isEmpty()) return null
        val layout = runCatching {
            String(Base64.getDecoder().decode(text.substring(second + 1, third).trim()), Charsets.UTF_8)
        }.getOrNull() ?: return null
        return Backup(settings, text.substring(third + 1), layout)
    }
}