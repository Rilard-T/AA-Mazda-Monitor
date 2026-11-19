package com.aadash.stats

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.aadash.obd.UserPids
import timber.log.Timber


class App : Application() {

    val logTree = CacheLogTree()


    override fun onCreate() {
        super.onCreate()
        // Сначала переносим значения из старого файла null.xml: до этого экраны настроек писали
        // туда, а чтение шло из <пакет>_preferences, поэтому тема после перезапуска сбрасывалась.
        com.aadash.prefs.MainPrefs.migrate(this)
        val mode = PreferenceManager.getDefaultSharedPreferences(this).getString("app_theme_mode", "system")
        AppCompatDelegate.setDefaultNightMode(when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
        Timber.plant(logTree)
        com.aadash.prefs.AppProfiles.migrate(this)
        UserPids.load(this)      // свои датчики нужны в каталоге до первого опроса
        fixAndroid14Perms()
    }
    
    fun fixAndroid14Perms() {
        for (file in getDir("car_sdk_impl", Context.MODE_PRIVATE).listFiles() ?: emptyArray()) {
            if (file.isDirectory) {
                for (subfile in file.listFiles() ?: emptyArray()) {
                    Timber.i("Setting read only permission for $subfile")
                    subfile.setReadOnly()
                }
            }
            Timber.i("Setting read only permission for $file")
            file.setReadOnly()
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }
}
