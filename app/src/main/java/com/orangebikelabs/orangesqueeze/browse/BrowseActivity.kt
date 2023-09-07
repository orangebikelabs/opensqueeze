/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.browse

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBar
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.core.view.isVisible
import androidx.fragment.app.commitNow
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.DrawerActivity
import com.orangebikelabs.orangesqueeze.artwork.ListPreloadFragment
import com.orangebikelabs.orangesqueeze.artwork.ShowArtworkFragment
import com.orangebikelabs.orangesqueeze.browse.node.BrowseNodeFragment
import com.orangebikelabs.orangesqueeze.common.NavigationItem
import com.orangebikelabs.orangesqueeze.common.SBPreferences
import com.orangebikelabs.orangesqueeze.common.event.AppPreferenceChangeEvent
import com.orangebikelabs.orangesqueeze.ui.MainActivity
import com.orangebikelabs.orangesqueeze.ui.TinyNowPlayingFragment
import com.squareup.otto.Subscribe

/**
 * @author tsandee
 */
class BrowseActivity : DrawerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBus.register(mEventReceiver)

        supportActionBar.setHomeButtonEnabled(true)
        supportActionBar.setDisplayHomeAsUpEnabled(true)
    }

    override fun onDestroy() {
        mBus.unregister(mEventReceiver)
        super.onDestroy()
    }

    override fun onLoadNavigationItem(navigationItem: NavigationItem) {
        super.onLoadNavigationItem(navigationItem)

        val newFragment = when (navigationItem.type) {
            NavigationItem.Type.BROWSENODE -> BrowseNodeFragment.newInstance(navigationItem)
            NavigationItem.Type.BROWSEREQUEST -> BrowseRequestFragment.newInstance(navigationItem)
            NavigationItem.Type.BROWSEARTWORK -> ShowArtworkFragment.newInstance(navigationItem)
            else -> throw IllegalStateException("Unexpected NavigationItem:$navigationItem")
        }
        val extras = Bundle(intent.extras)
        NavigationItem.removeNavigationItem(extras)

        val arguments = newFragment.requireArguments()
        arguments.putAll(extras)

        supportFragmentManager.commitNow {
            add(R.id.content_frame, newFragment)
            add(ListPreloadFragment.newInstance(), "preload")
            add(R.id.tinynowplaying_frame, TinyNowPlayingFragment.newInstance())
        }
    }

    override fun onStart() {
        super.onStart()
        syncHeaderFooterState()
    }

    override fun allowSnackbarDisplay(): Boolean {
        return true
    }

    @Suppress("deprecation")
    override fun applyDrawerState(drawerState: DrawerState) {
        if (drawerState === DrawerState.BROWSE) {
            supportActionBar.navigationMode = ActionBar.NAVIGATION_MODE_STANDARD
            supportActionBar.setDisplayShowTitleEnabled(true)
            supportActionBar.setTitle(R.string.left_drawer_title)
        } else if (drawerState === DrawerState.PLAYER) {
            supportActionBar.navigationMode = ActionBar.NAVIGATION_MODE_LIST
            supportActionBar.setDisplayShowTitleEnabled(false)
        } else {
            supportActionBar.navigationMode = ActionBar.NAVIGATION_MODE_LIST
            supportActionBar.setDisplayShowTitleEnabled(false)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            val upIntent = MainActivity.newIntent(this)
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
            // TODO do we want transitions for exiting?
            //overridePendingTransition(R.anim.in_from_left, android.R.anim.fade_out);
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun syncHeaderFooterState() {
        if (!SBPreferences.get().isBrowseActionBarEnabled) {
            supportActionBar.hide()
        } else {
            supportActionBar.show()
        }
        val tinyContainer = findViewById<View?>(R.id.tinynowplaying_frame)
        if (tinyContainer != null) {
            val visible = SBPreferences.get().isBrowseNowPlayingBarEnabled
            tinyContainer.isVisible = visible
        }
    }

    private val mEventReceiver: Any = object : Any() {
        @Subscribe
        fun whenAppPreferenceChanges(event: AppPreferenceChangeEvent?) {
            // just reset on any pref change for simplicity
            syncHeaderFooterState()
        }
    }
}