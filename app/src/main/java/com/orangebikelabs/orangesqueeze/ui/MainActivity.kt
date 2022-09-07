/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commitNow

import com.orangebikelabs.orangesqueeze.BuildConfig
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.DrawerActivity
import com.orangebikelabs.orangesqueeze.browse.BrowseRequestFragment
import com.orangebikelabs.orangesqueeze.browse.node.BrowseNodeFragment
import com.orangebikelabs.orangesqueeze.browse.node.NodeItem
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.common.event.PlayerBrowseMenuChangedEvent
import com.orangebikelabs.orangesqueeze.download.ViewDownloadsFragment
import com.orangebikelabs.orangesqueeze.players.ManagePlayersFragment
import com.squareup.otto.Subscribe
import java.util.concurrent.TimeUnit

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.FlowPreview

/**
 * Main activity that houses most of the app fragments. It uses the actionbar/navigationlist to support navigation between the various
 * screens.
 *
 * @author tbsandee@orangebikelabs.com
 */
class MainActivity : DrawerActivity() {

    private var title = ""

    /**
     * dumb hack to keep search widget expanded
     */
    private var hackKeepSearchExpanded = false

    /**
     * stores the currently displayed browse node id, so it doesn't need to get reloaded when the browse menu refreshes
     */
    private var currentLoadedBrowseNodeId: String? = null

    /**
     * flag stores whether or not an activity start is pending -- if so we don't issue some of the responses to new menus, etc to avoid overriding it
     */
    private var startActivityPending: Boolean = false

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // reset on create
        startActivityPending = false

        mBus.register(eventReceiver)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.tinynowplaying_frame, TinyNowPlayingFragment.newInstance())
            }

            title = getString(R.string.loading_menus_text)
        } else {
            // we're restoring from a previous state, restore the original title
            // fragments and other things will be restored automatically
            title = checkNotNull(savedInstanceState.getString(STATE_TITLE)) { "title should have been preserved" }
        }

        supportActionBar.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        // handle any directives from launcher
        if (LaunchFlags.gotoNowPlaying) {
            OSLog.i("MainActivity: Launching direct to NOWPLAYING activity")
            LaunchFlags.gotoNowPlaying = false

            startActivityPending = true
            startActivity(navigationManager.newNowPlayingIntent())
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mBus.unregister(eventReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(STATE_TITLE, title)
    }

    override fun allowSnackbarDisplay(): Boolean {
        return true
    }

    override fun applyDrawerState(drawerState: DrawerState) {
        if (drawerState !== DrawerState.BROWSE) {
            supportActionBar.title = title
        } else {
            supportActionBar.setTitle(R.string.left_drawer_title)
        }
    }

    override fun enableDrawerToggle(): Boolean {
        return true
    }

    override fun onNewIntentBehavior(intent: Intent) {
        super.onNewIntentBehavior(intent)

        if (intent.action == Actions.ACTION_GO_SEARCH) {
            expandSearchActionView()
            hackKeepSearchExpanded = true
        } else if (!hackKeepSearchExpanded) {
            // make sure search is collapsed when we come back to activity
            collapseSearchActionView()
        }
    }

    @FlowPreview
    override fun onLoadNavigationItem(navigationItem: NavigationItem) {
        super.onLoadNavigationItem(navigationItem)

        closeDrawers()

        var newFragment: androidx.fragment.app.Fragment? = null
        when (navigationItem.type) {
            NavigationItem.Type.DOWNLOADS -> {
                currentLoadedBrowseNodeId = null
                title = getString(navigationItem.type.titleRid)
                newFragment = ViewDownloadsFragment()
            }
            NavigationItem.Type.HOME, NavigationItem.Type.BROWSENODE, NavigationItem.Type.BROWSEREQUEST -> {
                val playerId = SBContextProvider.get().playerId
                if (playerId != null) {
                    val targetNodeId = navigationItem.nodeId
                    val items = NodeItem.getRootMenu(playerId)
                    for (item in items) {
                        val itemNodeId = item.id
                        if (itemNodeId == targetNodeId || targetNodeId == null) {
                            if (currentLoadedBrowseNodeId == itemNodeId) {
                                // skip loading, it's already loaded and maybe the menu refreshed
                                break
                            }
                            if (targetNodeId == null && item.menuElement.style == "itemplay") {
                                // not eligible for initial selection
                                continue
                            }
                            val newNavigationItem = if (navigationItem.type == NavigationItem.Type.HOME) {
                                item.navigationItem ?: continue
                            } else {
                                navigationItem
                            }
                            currentLoadedBrowseNodeId = itemNodeId
                            title = item.itemTitle

                            if (newNavigationItem.type === NavigationItem.Type.BROWSENODE) {
                                newFragment = BrowseNodeFragment.newInstance(newNavigationItem)
                            } else {
                                newFragment = BrowseRequestFragment.newInstance(newNavigationItem)
                            }
                            break
                        }
                    }
                }
            }
            NavigationItem.Type.PLAYERS -> {
                currentLoadedBrowseNodeId = null
                title = getString(navigationItem.type.titleRid)
                newFragment = ManagePlayersFragment()
            }
            else -> {
                AndroidSchedulers.mainThread().scheduleDirect({
                    navigationManager.navigateTo(navigationItem)
                }, 500, TimeUnit.MILLISECONDS)
            }
        }
        if (newFragment != null) {
            supportActionBar.title = title
            supportFragmentManager.commitNow {
                replace(R.id.content_frame, newFragment)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (hackKeepSearchExpanded) {
            AndroidSchedulers.mainThread().scheduleDirect({ this.expandSearchActionView() }, 500, TimeUnit.MILLISECONDS)

            hackKeepSearchExpanded = false
        }
    }

    override fun onStop() {
        super.onStop()

        // reset this when the next activity launches
        startActivityPending = false
    }

    private val eventReceiver = object : Any() {

        @Subscribe
        fun whenPlayerBrowseMenuChanged(event: PlayerBrowseMenuChangedEvent) {
            if (event.playerId == SBContextProvider.get().playerId) {
                OSLog.d("player menus changed, rebuilding nav tree (startActivityPending=$startActivityPending)")

                if (supportFragmentManager.findFragmentById(R.id.content_frame) == null && !startActivityPending) {
                    val defaultItem = NavigationItem.newFixedItem(this@MainActivity, NavigationItem.Type.HOME)
                    navigationManager.navigateTo(defaultItem)
                }
            }
        }
    }

    companion object {

        fun newIntent(context: Context): Intent {
            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            return intent
        }

        /**
         * save the actionbar title in the state when we rotato
         */
        private const val STATE_TITLE = BuildConfig.APPLICATION_ID + ".state.title"
    }
}