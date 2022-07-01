/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui

import android.os.Bundle
import androidx.fragment.app.commitNow
import androidx.preference.CheckBoxPreference
import com.orangebikelabs.orangesqueeze.R

/**
 * @author tbsandee@orangebikelabs.com
 */

class AdvancedPreferenceActivity : AbsPreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.advanced_preference_activity_name)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.toolbar_content, AdvancedPreferenceFragment())
            }
        }
    }

    class AdvancedPreferenceFragment : AbsPreferenceFragment() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.advanced_preferences)
        }

        override fun updatePreferences() {
            val playSilencePreference: CheckBoxPreference? = findPreference(getString(R.string.pref_silentaudiohack_key))
            val pauseOnHeadphoneDisconnectPreference: CheckBoxPreference? = findPreference(getString(R.string.pref_pauseonheadphonedisconnect_key))
            pauseOnHeadphoneDisconnectPreference?.isEnabled = playSilencePreference?.isChecked
                    ?: false
        }
    }
}
