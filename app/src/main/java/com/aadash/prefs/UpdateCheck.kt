package com.aadash.prefs

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

    /**
     * Номер сборки канала nav из имени версии: «0.9.0-nav.12» → 12.
     * CI ставит его в versionNameSuffix, в тег — как последнее число «nav-0.9.0-12».
     */
    fun navBuildOf(versionName: String?): Int =
        Regex("-nav\\.(\\d+)").find(versionName ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** Тег канала nav («nav-0.9.0-12») → (код версии, номер сборки); null — это не тег nav. */
    fun navTagParams(tag: String?): Pair<Int, Int>? {
        val m = Regex("^nav-(\\d+\\.\\d+\\.\\d+)-(\\d+)").find(tag?.trim() ?: "") ?: return null
        val code = versionCodeOf(m.groupValues[1]) ?: return null
        return code to m.groupValues[2].toInt()
    }

    /**
     * Сравнение сборок канала nav: сначала код версии, затем номер сборки CI —
     * пересборка той же версии (новый номер) тоже считается обновлением.
     */
    fun isNewerNav(tag: String?, currentCode: Int, currentBuild: Int): Boolean? {
        val (code, build) = navTagParams(tag) ?: return null
        return code > currentCode || (code == currentCode && build > currentBuild)
    }

    /**
     * Самый новый тег канала nav из atom-ленты релизов. Выпуски nav — предрелизы,
     * поэтому /releases/latest их не находит, а лента отдаётся без лимита GitHub API.
     */
    fun newestNavTag(feed: String?): String? {
        if (feed == null) return null
        return Regex("<title>\\s*(nav-\\d+\\.\\d+\\.\\d+-\\d+)\\s*</title>")
            .findAll(feed)
            .map { it.groupValues[1] }
            .maxByOrNull { val (code, build) = navTagParams(it)!!; code * 100_000L + build }
    }
}
