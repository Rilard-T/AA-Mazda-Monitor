package com.aatorque.prefs

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.aatorque.stats.R

class ProfilesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_App_Settings)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        title = getString(R.string.profile_settings_cat)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, ProfilesFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class ProfilesFragment : PreferenceFragmentCompat() {

    private var activePref: ListPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = null
        val ctx = requireContext()
        val screen = preferenceManager.createPreferenceScreen(ctx)
        preferenceScreen = screen

        fun category(title: String): PreferenceCategory {
            val c = PreferenceCategory(ctx)
            c.title = title
            screen.addPreference(c)
            return c
        }

        // --- активный профиль ---
        val cat = category(getString(R.string.profile_settings_cat))
        val active = ListPreference(ctx)
        active.key = "active_profile"
        active.title = getString(R.string.profile_active)
        active.dialogTitle = getString(R.string.profile_active)
        active.isPersistent = false
        refreshActiveList(active)
        active.setOnPreferenceChangeListener { _, newValue ->
            val id = newValue.toString()
            if (id == AppProfiles.activeId(ctx)) return@setOnPreferenceChangeListener true
            AppProfiles.setActive(ctx, id)
            promptRestart()
            true
        }
        cat.addPreference(active)
        activePref = active

        // --- создать ---
        val create = Preference(ctx)
        create.title = getString(R.string.profile_create)
        create.setOnPreferenceClickListener {
            val input = EditText(ctx)
            input.hint = getString(R.string.profile_name_hint)
            AlertDialog.Builder(requireActivity())
                .setTitle(getString(R.string.profile_create))
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val name = input.text.toString()
                    val id = AppProfiles.create(ctx, name)
                    if (id == null) {
                        Toast.makeText(ctx, "Имя занято или пустое", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, getString(R.string.profile_created), Toast.LENGTH_SHORT).show()
                        refreshActiveList(active)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
        cat.addPreference(create)

        // --- переименовать ---
        val rename = Preference(ctx)
        rename.title = getString(R.string.profile_rename)
        rename.setOnPreferenceClickListener {
            val profiles = AppProfiles.profiles(ctx)
            val names = profiles.map { it.name }.toTypedArray()
            val ids = profiles.map { it.id }.toTypedArray()
            AlertDialog.Builder(requireActivity())
                .setTitle(getString(R.string.profile_rename))
                .setItems(names) { _, which ->
                    val id = ids[which]
                    val input = EditText(ctx)
                    input.setText(profiles[which].name)
                    AlertDialog.Builder(requireActivity())
                        .setTitle(getString(R.string.profile_rename))
                        .setView(input)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val ok = AppProfiles.rename(ctx, id, input.text.toString())
                            if (ok) {
                                Toast.makeText(ctx, getString(R.string.profile_renamed), Toast.LENGTH_SHORT).show()
                                refreshActiveList(active)
                            } else {
                                Toast.makeText(ctx, "Имя занято или пустое", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
        cat.addPreference(rename)

        // --- удалить ---
        val delete = Preference(ctx)
        delete.title = getString(R.string.profile_delete)
        delete.setOnPreferenceClickListener {
            val profiles = AppProfiles.profiles(ctx)
            val activeId = AppProfiles.activeId(ctx)
            val deletable = profiles.filter { it.id != activeId }
            if (deletable.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.profile_cannot_delete_last), Toast.LENGTH_SHORT).show()
                return@setOnPreferenceClickListener true
            }
            val names = deletable.map { it.name }.toTypedArray()
            val ids = deletable.map { it.id }.toTypedArray()
            AlertDialog.Builder(requireActivity())
                .setTitle(getString(R.string.profile_delete))
                .setItems(names) { _, which ->
                    AlertDialog.Builder(requireActivity())
                        .setTitle(getString(R.string.profile_delete))
                        .setMessage(names[which])
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            val ok = AppProfiles.delete(ctx, ids[which])
                            if (ok) {
                                Toast.makeText(ctx, getString(R.string.profile_deleted), Toast.LENGTH_SHORT).show()
                                refreshActiveList(active)
                            } else {
                                Toast.makeText(ctx, getString(R.string.profile_cannot_delete_active), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(android.R.string.no, null)
                        .show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
        cat.addPreference(delete)
    }

    private fun refreshActiveList(active: ListPreference) {
        val ctx = requireContext()
        val profiles = AppProfiles.profiles(ctx)
        active.entries = profiles.map { it.name }.toTypedArray()
        active.entryValues = profiles.map { it.id }.toTypedArray()
        active.value = AppProfiles.activeId(ctx)
        active.summary = AppProfiles.activeName(ctx)
    }

    private fun promptRestart() {
        AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.profile_switch_restart))
            .setPositiveButton(getString(R.string.profile_restart)) { _, _ ->
                restartApp()
            }
            .setNegativeButton(getString(R.string.profile_later), null)
            .show()
    }

    private fun restartApp() {
        val ctx = requireContext().applicationContext
        // остановить службу OBD
        com.aatorque.obd.ObdBridgeService.stop(ctx)
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}