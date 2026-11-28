package com.aadash.prefs

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AppProfile(val id: String, val name: String)

object AppProfiles {

    private const val META_PREFS = "app_profile_meta"
    private const val KEY_PROFILES = "profiles_json"
    private const val KEY_ACTIVE = "active_id"
    private const val KEY_UNIVERSAL_SEEDED = "universal_seeded"

    private const val DEFAULT_ID = "default"
    private const val DEFAULT_NAME = "Mazda Diesel"
    private const val UNIVERSAL_ID = "universal"

    @Volatile private var migrated = false
    @Volatile private var cachedProfiles: List<AppProfile>? = null
    @Volatile private var cachedActiveId: String? = null

    fun meta(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)

    private fun universalName(context: Context): String = try {
        context.getString(com.aadash.stats.R.string.profile_universal_name)
    } catch (_: Exception) {
        "Universal OBD-II"
    }

    private fun seedUniversalLayoutIfMissing(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(navPrefsName(UNIVERSAL_ID), Context.MODE_PRIVATE)
            if (!prefs.contains("layout_json")) {
                val json = com.aadash.nav.NavLayoutStore.toJson(com.aadash.nav.NavLayoutStore.universalLayout())
                prefs.edit().putString("layout_json", json).apply()
            }
        } catch (_: Exception) { }
    }

    @Synchronized
    fun migrate(context: Context) {
        if (migrated) return
        val m = meta(context)
        if (!m.contains(KEY_PROFILES)) {
            val arr = JSONArray()
            arr.put(JSONObject().put("id", DEFAULT_ID).put("name", DEFAULT_NAME))
            arr.put(JSONObject().put("id", UNIVERSAL_ID).put("name", universalName(context)))
            m.edit()
                .putString(KEY_PROFILES, arr.toString())
                .putString(KEY_ACTIVE, DEFAULT_ID)
                .putBoolean(KEY_UNIVERSAL_SEEDED, true)
                .apply()
            seedUniversalLayoutIfMissing(context)
        } else if (!m.getBoolean(KEY_UNIVERSAL_SEEDED, false)) {
            // Безопасное добавление универсального профиля существующим пользователям без изменения их профилей и активного выбора
            try {
                val arr = JSONArray(m.getString(KEY_PROFILES, "[]"))
                var hasUniversal = false
                for (i in 0 until arr.length()) {
                    if (arr.getJSONObject(i).optString("id") == UNIVERSAL_ID) {
                        hasUniversal = true
                        break
                    }
                }
                if (!hasUniversal) {
                    arr.put(JSONObject().put("id", UNIVERSAL_ID).put("name", universalName(context)))
                    m.edit().putString(KEY_PROFILES, arr.toString()).apply()
                    seedUniversalLayoutIfMissing(context)
                }
            } catch (_: Exception) { }
            m.edit().putBoolean(KEY_UNIVERSAL_SEEDED, true).apply()
        }
        migrated = true
    }

    fun profiles(context: Context): List<AppProfile> {
        cachedProfiles?.let { return it }
        synchronized(this) {
            cachedProfiles?.let { return it }
            migrate(context)
            val arr = JSONArray(meta(context).getString(KEY_PROFILES, "[]"))
            val out = ArrayList<AppProfile>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(AppProfile(o.getString("id"), o.getString("name")))
            }
            if (out.isEmpty()) out.add(AppProfile(DEFAULT_ID, DEFAULT_NAME))
            val ro = out.toList()
            cachedProfiles = ro
            return ro
        }
    }

    fun activeId(context: Context): String {
        cachedActiveId?.let { return it }
        synchronized(this) {
            cachedActiveId?.let { return it }
            val list = profiles(context)
            val id = meta(context).getString(KEY_ACTIVE, DEFAULT_ID) ?: DEFAULT_ID
            val resolved = if (list.any { it.id == id }) id else list.first().id
            cachedActiveId = resolved
            return resolved
        }
    }

    fun activeName(context: Context): String {
        val id = activeId(context)
        return profiles(context).firstOrNull { it.id == id }?.name ?: DEFAULT_NAME
    }

    @Synchronized
    fun setActive(context: Context, id: String) {
        if (profiles(context).any { it.id == id }) {
            cachedActiveId = id
            meta(context).edit().putString(KEY_ACTIVE, id).apply()
        }
    }

    fun create(context: Context, name: String, copyFrom: String? = activeId(context)): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val list = profiles(context).toMutableList()
        if (list.any { it.name.equals(trimmed, ignoreCase = true) }) return null
        val id = "p_" + UUID.randomUUID().toString().replace("-", "").take(12)
        list.add(AppProfile(id, trimmed))
        saveList(context, list)
        if (copyFrom != null && copyFrom != id) {
            copyPrefs(context, obdPrefsName(copyFrom), obdPrefsName(id))
            copyPrefs(context, navPrefsName(copyFrom), navPrefsName(id))
            copyPrefs(context, userPidsPrefsName(copyFrom), userPidsPrefsName(id))
            copyFile(context, userPrefsFileName(copyFrom), userPrefsFileName(id))
        }
        return id
    }

    private fun copyPrefs(context: Context, from: String, to: String) {
        val src = context.getSharedPreferences(from, Context.MODE_PRIVATE)
        val dst = context.getSharedPreferences(to, Context.MODE_PRIVATE).edit()
        dst.clear()
        for ((k, v) in src.all) {
            when (v) {
                is Boolean -> dst.putBoolean(k, v)
                is Float -> dst.putFloat(k, v)
                is Int -> dst.putInt(k, v)
                is Long -> dst.putLong(k, v)
                is String -> dst.putString(k, v)
                is Set<*> -> dst.putStringSet(k, v.filterIsInstance<String>().toSet())
            }
        }
        dst.apply()
    }

    private fun copyFile(context: Context, from: String, to: String) {
        try {
            val src = java.io.File(context.applicationContext.filesDir, from)
            val dst = java.io.File(context.applicationContext.filesDir, to)
            if (src.exists()) src.copyTo(dst, overwrite = true)
        } catch (_: Exception) { }
    }

    fun rename(context: Context, id: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        val list = profiles(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return false
        if (list.any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) return false
        list[idx] = AppProfile(id, trimmed)
        saveList(context, list)
        return true
    }

    fun delete(context: Context, id: String): Boolean {
        val list = profiles(context).toMutableList()
        if (list.size <= 1) return false
        if (id == activeId(context)) return false
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return false
        list.removeAt(idx)
        saveList(context, list)
        deletePrefsFile(context, obdPrefsName(id))
        deletePrefsFile(context, navPrefsName(id))
        deletePrefsFile(context, userPidsPrefsName(id))
        deleteDataStoreFile(context, userPrefsFileName(id))
        return true
    }

    private fun saveList(context: Context, list: List<AppProfile>) {
        val arr = JSONArray()
        for (p in list) arr.put(JSONObject().put("id", p.id).put("name", p.name))
        cachedProfiles = list.toList()
        meta(context).edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun deletePrefsFile(context: Context, name: String) {
        try { context.applicationContext.deleteSharedPreferences(name) } catch (_: Exception) {}
    }

    private fun deleteDataStoreFile(context: Context, name: String) {
        try { context.applicationContext.deleteFile(name) } catch (_: Exception) {}
    }

    fun isDefault(id: String): Boolean = id == DEFAULT_ID

    fun isUniversal(context: Context): Boolean = activeId(context) == UNIVERSAL_ID

    fun obdPrefsName(id: String): String =
        if (id == DEFAULT_ID) "obd_bridge" else "obd_bridge_$id"

    fun navPrefsName(id: String): String =
        if (id == DEFAULT_ID) "nav_layout" else "nav_layout_$id"

    fun userPidsPrefsName(id: String): String =
        if (id == DEFAULT_ID) "user_pids" else "user_pids_$id"

    fun userPrefsFileName(id: String): String =
        if (id == DEFAULT_ID) "user_prefs.pb" else "user_prefs_$id.pb"

    // --- удобные перегрузки по Context ---

    fun obdPrefsName(context: Context): String = obdPrefsName(activeId(context))
    fun navPrefsName(context: Context): String = navPrefsName(activeId(context))
    fun userPidsPrefsName(context: Context): String = userPidsPrefsName(activeId(context))
    fun userPrefsFileName(context: Context): String = userPrefsFileName(activeId(context))

    const val DEFAULT_ID_VALUE = DEFAULT_ID
}