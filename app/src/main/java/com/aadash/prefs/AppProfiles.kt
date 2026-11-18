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

    private const val DEFAULT_ID = "default"
    private const val DEFAULT_NAME = "Мой автомобиль"

    fun meta(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)

    fun migrate(context: Context) {
        val m = meta(context)
        if (m.contains(KEY_PROFILES)) return
        val arr = JSONArray()
        arr.put(JSONObject().put("id", DEFAULT_ID).put("name", DEFAULT_NAME))
        m.edit().putString(KEY_PROFILES, arr.toString()).putString(KEY_ACTIVE, DEFAULT_ID).apply()
    }

    fun profiles(context: Context): List<AppProfile> {
        migrate(context)
        val arr = JSONArray(meta(context).getString(KEY_PROFILES, "[]"))
        val out = ArrayList<AppProfile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(AppProfile(o.getString("id"), o.getString("name")))
        }
        if (out.isEmpty()) out.add(AppProfile(DEFAULT_ID, DEFAULT_NAME))
        return out
    }

    fun activeId(context: Context): String {
        migrate(context)
        val id = meta(context).getString(KEY_ACTIVE, DEFAULT_ID) ?: DEFAULT_ID
        return if (profiles(context).any { it.id == id }) id else profiles(context).first().id
    }

    fun activeName(context: Context): String =
        profiles(context).firstOrNull { it.id == activeId(context) }?.name ?: DEFAULT_NAME

    fun setActive(context: Context, id: String) {
        if (profiles(context).any { it.id == id }) {
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
        meta(context).edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    private fun deletePrefsFile(context: Context, name: String) {
        try { context.applicationContext.deleteSharedPreferences(name) } catch (_: Exception) {}
    }

    private fun deleteDataStoreFile(context: Context, name: String) {
        try { context.applicationContext.deleteFile(name) } catch (_: Exception) {}
    }

    fun isDefault(id: String): Boolean = id == DEFAULT_ID

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