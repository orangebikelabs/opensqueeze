/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui

import android.Manifest
import android.os.Bundle
import androidx.fragment.app.commitNow
import androidx.preference.Preference
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.files.folderChooser
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.google.android.material.snackbar.Snackbar
import com.google.common.base.Joiner
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.SBPreferences
import com.orangebikelabs.orangesqueeze.compat.Compat
import permissions.dispatcher.*
import java.io.File

/**
 * @author tbsandee@orangebikelabs.com
 */
class TrackDownloadPreferenceActivity : AbsPreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.toolbar_content, TrackDownloadPreferenceFragment())
            }
        }
    }

    @RuntimePermissions
    class TrackDownloadPreferenceFragment : AbsPreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.trackdownload_preferences)
        }

        override fun updatePreferences() {
            val downloadLocationPref = findPreference<Preference>(getString(R.string.pref_trackdownload_location_key))
            if (downloadLocationPref != null) {
                downloadLocationPref.summary = locationSummary
                downloadLocationPref.setOnPreferenceChangeListener { _, _ ->
                    downloadLocationPref.summary = locationSummary
                    true
                }
                downloadLocationPref.setOnPreferenceClickListener {
                    showDownloadChooserWithPermissionCheck()
                    true
                }
            }

            val downloadFormat = findPreference<Preference>(getString(R.string.pref_trackdownload_transcodeformat_key))
            if (downloadFormat != null) {
                val value = SBPreferences.get().transcodeFormat
                downloadFormat.summary = getString(R.string.pref_trackdownload_transcodeformat_summary, value)
            }

            val downloadOptions = findPreference<Preference>(getString(R.string.pref_trackdownload_transcodeoptions_key))
            if (downloadOptions != null) {
                val options = SBPreferences.get().transcodeOptions
                val formatted = Joiner.on(",").join(options)
                downloadOptions.summary = getString(R.string.pref_trackdownload_transcodeoptions_summary, formatted)
            }
        }

        private val locationSummary: String
            get() = getString(R.string.pref_trackdownload_location_summary, visibleLocation)

        private val visibleLocation: String
            get() {
                val location = SBPreferences.get().downloadLocation
                val writable = location.canWrite()
                return if (!writable) {
                    location.path + " (WARNING: OS reports non-writable, check app permissions)"
                } else {
                    location.path + " (OK)"
                }
            }

        @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        fun showDownloadChooser() {
            val currentDownloadLocation = SBPreferences.get().downloadLocation

            val items = Compat.getPublicMediaDirs().toMutableList()

            // if the current download location is NOT found in the list already, add it in
            if (items.firstOrNull { it.absolutePath == currentDownloadLocation.absolutePath } == null) {
                items += currentDownloadLocation
            }

            val stringItems = items.map { it.path }

            val selectedIndex = items.indexOfFirst { it.absolutePath == currentDownloadLocation.absolutePath }
            MaterialDialog(requireContext()).show {
                lifecycleOwner(this@TrackDownloadPreferenceFragment)
                title(res = R.string.pref_trackdownload_location_title)
                listItemsSingleChoice(items = stringItems, initialSelection = selectedIndex) { _, ndx, _ ->
                    val file = items[ndx]
                    SBPreferences.get().downloadLocation = file
                    updatePreferences()
                }
                @Suppress("DEPRECATION")
                neutralButton(res = R.string.custom_path) {
                    showCustomPathChooserWithPermissionCheck()
                }
                positiveButton(res = R.string.ok)
                negativeButton(res = R.string.cancel)
            }
        }

        @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        fun showCustomPathChooser() {

            val defaultValue = File(SBPreferences.get().downloadLocation.absolutePath)

            MaterialDialog(requireContext()).show {
                lifecycleOwner(this@TrackDownloadPreferenceFragment)
                folderChooser(requireContext(), initialDirectory = defaultValue, emptyTextRes = R.string.empty_text) { _, folder ->
                    SBPreferences.get().downloadLocation = folder
                    updatePreferences()
                }
                title(res = R.string.pref_trackdownload_location_title)
                negativeButton(res = R.string.cancel)
                positiveButton(res = R.string.ok)
            }
        }

        @OnShowRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        fun onShowRationale(request: PermissionRequest) {
            MaterialDialog(requireContext()).show {
                lifecycleOwner(this@TrackDownloadPreferenceFragment)
                title(res = R.string.permission_write_external_storage_rationale_title)
                message(res = R.string.permission_write_external_storage_rationale_content)
                positiveButton(res = R.string.ok) {
                    request.proceed()
                }
            }
        }

        @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE)
        fun showDeniedReadExternalStorage() {
            Snackbar.make(requireView(), R.string.permission_write_external_storage_denied, Snackbar.LENGTH_LONG).show()
        }

        @OnNeverAskAgain(Manifest.permission.READ_EXTERNAL_STORAGE)
        fun showNeverAskReadExternalStorage() {
            Snackbar.make(requireView(), R.string.permission_write_external_storage_neverask, Snackbar.LENGTH_LONG)
                    .show()
        }

        @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        fun showDeniedExternalStorage() {
            Snackbar.make(requireView(), R.string.permission_write_external_storage_denied, Snackbar.LENGTH_LONG).show()
        }

        @OnNeverAskAgain(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        fun showNeverAskExternalStorage() {
            Snackbar.make(requireView(), R.string.permission_write_external_storage_neverask, Snackbar.LENGTH_LONG)
                    .show()
        }
    }
}
