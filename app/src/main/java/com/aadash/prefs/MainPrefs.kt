package com.aadash.prefs

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Переход на штатный файл настроек (`<пакет>_preferences`).
 *
 * Экраны настроек создавали PreferenceManager и принудительно ставили
 * `sharedPreferencesName = null`. androidx передаёт это имя прямо в
 * `Context.getSharedPreferences`, поэтому все значения писались в файл `null.xml`, а [com.aadash.stats.App]
 * и [AppAccent] читали `<пакет>_preferences`. Из-за этого выбор темы и акцента не применялся, а после
 * перезапуска тема возвращалась к системной.
 *
 * Миграция переносит уже сохранённые значения в штатный файл и только после успешной записи удаляет
 * старый, поэтому настройки пользователей не теряются.
 */
object MainPrefs {

    /** Имя файла, которое получалось из `sharedPreferencesName = null`: null + ".xml". */
    private const val LEGACY_NAME = "null"

    fun migrate(context: Context) {
        val ctx = context.applicationContext
        val legacy = ctx.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) {
            ctx.deleteSharedPreferences(LEGACY_NAME)
            return
        }
        val target = PreferenceManager.getDefaultSharedPreferences(ctx)
        val edit = target.edit()
        for ((key, value) in legacy.all) {
            if (key == AppAccent.LEGACY_KEY || key == AppAccent.KEY) continue
            // Значения из штатного файла важнее: там уже могли появиться новые настройки.
            if (target.contains(key)) continue
            when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is String -> edit.putString(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        val legacyAccent = legacy.all[AppAccent.LEGACY_KEY] as? String
        if (legacyAccent != null && !target.contains(AppAccent.KEY)) {
            edit.putInt(AppAccent.KEY, AppAccent.seedForPreset(ctx, legacyAccent))
        }
        if (edit.commit()) ctx.deleteSharedPreferences(LEGACY_NAME)
    }
}
