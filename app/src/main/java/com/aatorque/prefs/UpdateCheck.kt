package com.aatorque.prefs

/**
 * Сравнение версий при проверке обновлений. Код версии считается по той же формуле,
 * что и в app/build.gradle: major * 100000 + minor * 1000 + patch.
 * Сравнение по числу, а не по строкам, поэтому «0.10.2» корректно новее «0.9.1».
 */
object UpdateCheck {

    fun versionCode(major: Int, minor: Int, patch: Int): Int = major * 100000 + minor * 1000 + patch

    /** Тег релиза («v0.8.4», «0.8.4», «0.8.4-beta») → код версии; null, если тег не разобрать. */
    fun versionCodeOf(tag: String?): Int? {
        val core = tag?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        val m = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(core) ?: return null
        val (major, minor, patch) = m.destructured
        return versionCode(major.toInt(), minor.toInt(), patch.toInt())
    }

    /** true — на сервере новее; false — та же или старая; null — тег не разобран. */
    fun isNewer(tag: String?, currentCode: Int): Boolean? =
        versionCodeOf(tag)?.let { it > currentCode }
}
