/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.ui

import android.Manifest
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.fragment.app.commitNow
import com.google.android.material.snackbar.Snackbar

import com.afollestad.materialdialogs.MaterialDialog
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider
import com.orangebikelabs.orangesqueeze.common.Constants
import com.orangebikelabs.orangesqueeze.common.OnCallMuteBehavior
import com.orangebikelabs.orangesqueeze.common.SBContextProvider
import com.orangebikelabs.orangesqueeze.common.SBPreferences
import com.orangebikelabs.orangesqueeze.players.SqueezePlayerHelper

import java.util.Locale

import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.orangebikelabs.orangesqueeze.R
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnNeverAskAgain
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.OnShowRationale
import permissions.dispatcher.PermissionRequest
import permissions.dispatcher.RuntimePermissions
import kotlin.math.min

/**
 * @author tbsandee@orangebikelabs.com
 */
class MainPreferenceActivity : AbsPreferenceActivity() {
    companion object {
        private const val MINIMUM_GRID_DPWIDTH = 112
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.main_preference_activity_name)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.toolbar_content, MainPreferenceFragment())
            }
        }
    }

    @RuntimePermissions
    class MainPreferenceFragment : AbsPreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.main_preferences)
            updatePreferences()
        }

        override fun updatePreferences() {
            val cache = CacheServiceProvider.get()

            val themePref = findPreference<ListPreference>(SBPreferences.get().themeIdKey)
            themePref?.setOnPreferenceChangeListener { _, newValue ->
                val theme = SBPreferences.ThemePreference.lookup(newValue)
                AppCompatDelegate.setDefaultNightMode(theme.nightMode)
                true
            }

            val storageCachePref = findPreference<Preference>(getString(R.string.pref_cache_storagesize_key))
            storageCachePref?.summary = getString(R.string.pref_cache_storagesize_summary, (cache.configuration.maxExternalSize / Constants.MB).toString())

            val languagePref = findPreference<ListPreference>(getString(R.string.pref_language_key))
            if (languagePref != null) {
                languagePref.onPreferenceChangeListener = null

                val savedLanguage = Locale(SBPreferences.get().selectedLanguage).language
                if (savedLanguage == Locale("fr").language) {
                    languagePref.value = "fr"
                } else if (savedLanguage == Locale("de").language) {
                    languagePref.value = "de"
                } else {
                    languagePref.value = "en"
                }
                languagePref.setOnPreferenceChangeListener { _, newValue ->
                    SBPreferences.get().selectedLanguage = newValue as String
                    ActivityCompat.recreate(requireActivity())
                    true
                }
            }

            // if SqueezePlayer isn't installed, don't show preference
            val generalCat = findPreference<PreferenceCategory>(getString(R.string.pref_general_category_key))
            if (generalCat != null) {
                val squeezePlayerPref = findPreference<Preference>(getString(R.string.pref_autolaunch_squeezeplayer_key))
                if (squeezePlayerPref != null) {
                    if (!SqueezePlayerHelper.isAvailable(requireContext())) {
                        generalCat.removePreference(squeezePlayerPref)
                    }
                }
            }

            // initialize this from pref
            val use24HourTimeFormat = findPreference<Preference>(getString(R.string.pref_use24hourtimeformat_key))
            use24HourTimeFormat?.setDefaultValue(SBPreferences.get().shouldUse24HourTimeFormat())

            val pref = findPreference<Preference>(getString(R.string.pref_automaticmute_key))
            pref?.setOnPreferenceClickListener {
                showAutomaticMuteOptionsWithPermissionCheck()
                true
            }
            val currentOrdinal = SBPreferences.get().onCallBehavior.ordinal
            val value = resources.getStringArray(R.array.pref_automaticmute_entries)[currentOrdinal]
            pref?.summary = getString(R.string.pref_automaticmute_summary, value)

            val cacheLocationPref = findPreference<Preference>(getString(R.string.pref_cachelocation_key))
            if (cacheLocationPref != null) {
                val loc = getString(SBPreferences.get().cacheLocation.rid)
                cacheLocationPref.summary = getString(R.string.pref_cachelocation_summary, loc)
            }

            val albumSortPref = findPreference<Preference>(getString(R.string.pref_browse_albumsort_key))
            if (albumSortPref != null) {
                albumSortPref.isEnabled = SBContextProvider.get().isConnected
            }

            val cellCountPref: ListPreference? = findPreference(getString(R.string.pref_browse_gridcellcount_key))
            if (cellCountPref != null) {
                val dm = resources.displayMetrics
                val dpWidth = (min(dm.widthPixels, dm.heightPixels) / dm.density).toInt()

                val entries = mutableListOf<String>()
                entries += getString(R.string.pref_browse_gridcellcount_uselist)

                val values = mutableListOf<String>()
                values += "0"

                var i = 1
                while (i * MINIMUM_GRID_DPWIDTH <= dpWidth) {
                    entries += i.toString()
                    values += i.toString()
                    i++
                }

                cellCountPref.entries = entries.toTypedArray()
                cellCountPref.entryValues = values.toTypedArray()
                cellCountPref.setDefaultValue(SBPreferences.get().browseGridCount.toString())
                cellCountPref.value = SBPreferences.get().browseGridCount.toString()
            }

        }

        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
            onRequestPermissionsResult(requestCode, grantResults)
        }

        @NeedsPermission(Manifest.permission.READ_PHONE_STATE)
        fun showAutomaticMuteOptions() {
            val initialIndex = SBPreferences.get().onCallBehavior.ordinal
            MaterialDialog(requireContext()).show {
                lifecycleOwner(this@MainPreferenceFragment)
                title(res = R.string.pref_automaticmute_title)
                listItemsSingleChoice(res = R.array.pref_automaticmute_entries, initialSelection = initialIndex) { _, ndx, _ ->
                    val behavior = OnCallMuteBehavior.values()[ndx]
                    SBPreferences.get().onCallBehavior = behavior
                }
                positiveButton(res = R.string.ok)
                negativeButton(res = R.string.cancel)
            }
        }

        @OnShowRationale(Manifest.permission.READ_PHONE_STATE)
        fun onShowRationale(request: PermissionRequest) {
            MaterialDialog(requireContext()).show {
                lifecycleOwner(this@MainPreferenceFragment)
                title(res = R.string.permission_read_phone_state_rationale_title)
                message(res = R.string.permission_read_phone_state_rationale_content)
                positiveButton(res = R.string.ok) {
                    request.proceed()
                }
                negativeButton(res = R.string.cancel)
            }
        }

        @OnPermissionDenied(Manifest.permission.READ_PHONE_STATE)
        fun showDeniedPhone() {
            Snackbar.make(requireView(), R.string.permission_read_phone_state_denied, Snackbar.LENGTH_LONG)
                    .show()
        }

        @OnNeverAskAgain(Manifest.permission.READ_PHONE_STATE)
        fun showNeverAskPhone() {
            Snackbar.make(requireView(), R.string.permission_read_phone_state_neverask, Snackbar.LENGTH_LONG)
                    .show()
        }
    }
}
