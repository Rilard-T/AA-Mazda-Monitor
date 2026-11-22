package com.aadash.prefs

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.appcompat.app.AppCompatDelegate
import com.aadash.stats.NotiService
import com.aadash.stats.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findPreference<Preference>("navEditor")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), com.aadash.nav.NavEditorActivity::class.java))
            true
        }
        findPreference<Preference>("profile_manager")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ProfilesActivity::class.java))
            true
        }
        findPreference<Preference>("media_access")?.setOnPreferenceClickListener {
            if (!NotiService.isNotificationAccessEnabled(requireContext())) {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            putExtra(
                                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                NotiService::class.java.canonicalName
                            )
                        }
                    }
                )
            } else {
                Toast.makeText(requireContext(), "Доступ уже включён", Toast.LENGTH_SHORT).show()
            }
            true
        }
        findPreference<Preference>("open_obd_settings")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), com.aadash.obd.ObdSettingsActivity::class.java))
            true
        }
        findPreference<Preference>("export_regen_log")?.setOnPreferenceClickListener {
            (requireActivity() as SettingsActivity).exportRegenLog()
            true
        }
        findPreference<Preference>("import_regen_log")?.setOnPreferenceClickListener {
            (requireActivity() as SettingsActivity).importRegenLog()
            true
        }
        findPreference<Preference>("open_experimental")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), com.aadash.obd.ObdExperimentalActivity::class.java))
            true
        }
        findPreference<Preference>("credits")?.setOnPreferenceClickListener {
            (requireActivity() as SettingsActivity).openCredits()
            true
        }
        findPreference<Preference>("report_error")?.setOnPreferenceClickListener {
            (requireActivity() as SettingsActivity).logsToClipboard()
            true
        }
        findPreference<Preference>("force_update")?.setOnPreferenceClickListener {
            (requireActivity() as SettingsActivity).forceUpdate()
            true
        }
        findPreference<Preference>("exit_app")?.setOnPreferenceClickListener {
            (requireActivity() as SettingsActivity).exitApp()
            true
        }
        findPreference<ListPreference>("app_theme_mode")?.setOnPreferenceChangeListener { _, newValue ->
            val mode = when (newValue) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            true
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)
    }

    override fun onStart() {
        super.onStart()
        (requireActivity() as SettingsActivity).supportActionBar!!.subtitle = null
    }
}
