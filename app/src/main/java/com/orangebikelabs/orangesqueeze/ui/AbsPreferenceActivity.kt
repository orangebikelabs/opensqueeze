/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import android.view.MenuItem

import com.orangebikelabs.orangesqueeze.app.SBActivity
import com.orangebikelabs.orangesqueeze.common.BusProvider
import com.orangebikelabs.orangesqueeze.common.OSExecutors
import com.orangebikelabs.orangesqueeze.common.event.AppPreferenceChangeEvent
import com.squareup.otto.Subscribe

import androidx.preference.PreferenceFragmentCompat
import com.orangebikelabs.orangesqueeze.databinding.ToolbarActivityBinding
import com.orangebikelabs.orangesqueeze.databinding.ToolbarBinding

/**
 * @author tbsandee@orangebikelabs.com
 */
abstract class AbsPreferenceActivity : SBActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityBinding = ToolbarActivityBinding.inflate(layoutInflater)
        val toolbar = ToolbarBinding.bind(activityBinding.root)
        contentView = activityBinding.root

        setSupportActionBar(toolbar.toolbar)

        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val upIntent = Intent(this, MainActivity::class.java)
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is not part of the application's task, so create a new task
                // with a synthesized back stack.
                TaskStackBuilder.create(this).addNextIntent(upIntent).startActivities()
                finish()
            } else {
                // This activity is part of the application's task, so simply
                // navigate up to the hierarchical parent activity.
                finish()
            }
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    abstract class AbsPreferenceFragment : PreferenceFragmentCompat() {
        private val eventReceiver = object : Any() {
            @Subscribe
            fun whenAppPreferenceChanges(event: AppPreferenceChangeEvent) {
                // post this at the end so that the cache has had time to update
                // based on new prefs
                OSExecutors.getMainThreadExecutor().execute { callUpdatePreferences() }
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            BusProvider.getInstance().register(eventReceiver)
        }

        override fun onDestroy() {
            super.onDestroy()

            BusProvider.getInstance().unregister(eventReceiver)
        }

        override fun onStart() {
            super.onStart()

            updatePreferences()
        }

        private fun callUpdatePreferences() {
            if (context != null) {
                updatePreferences()
            }
        }

        abstract fun updatePreferences()
    }
}
