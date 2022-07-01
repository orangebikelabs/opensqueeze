/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import com.orangebikelabs.orangesqueeze.BuildConfig

import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.DrawerActivity
import com.orangebikelabs.orangesqueeze.browse.AbsBrowseFragment
import com.orangebikelabs.orangesqueeze.browse.BrowseActivity
import com.orangebikelabs.orangesqueeze.browse.search.SearchActivity
import com.orangebikelabs.orangesqueeze.nowplaying.NowPlayingActivity
import com.orangebikelabs.orangesqueeze.ui.CustomizeRootMenuActivity
import com.orangebikelabs.orangesqueeze.ui.MainActivity

/**
 * Helper class that handles all of our navigation within the navbar tree.
 *
 * @author tsandee
 */
class NavigationManager(private val activity: DrawerActivity) {

    companion object {
        const val ACTIVITY_REQUESTCODE = 1

        const val RESULT_NAVITEM_CHANGED = Activity.RESULT_FIRST_USER + 1
        const val RESULT_GOTO_LEVEL = Activity.RESULT_FIRST_USER + 2

        const val EXTRA_TARGET_LEVEL = BuildConfig.APPLICATION_ID + ".extra.navigationTargetLevel"

        const val EXTRA_NAVIGATION_STACK = BuildConfig.APPLICATION_ID + ".extra.navigationStack"

        fun newBlankNowPlayingIntent(context: Context): Intent {
            val newStack = arrayListOf(
                    NavigationItem.newFixedItem(context, NavigationItem.Type.HOME)
            )

            // we will fill in the pendingintent on the activity side
            val newItem = NavigationItem.newFixedItem(context, NavigationItem.Type.NOWPLAYING)
            val retval = Intent(context, NowPlayingActivity::class.java)
            retval.putExtra(NowPlayingActivity.EXTRA_RECREATE_MAIN_ACTIVITY_ON_UP, true)
            NavigationItem.putNavigationItem(retval, newItem)
            retval.putParcelableArrayListExtra(EXTRA_NAVIGATION_STACK, newStack)
            return retval
        }

        fun newDownloadsIntent(context: Context): Intent {
            val item = NavigationItem.newFixedItem(context, NavigationItem.Type.DOWNLOADS)
            return newNavigationIntent(context, item)
        }

        fun newSearchIntent(context: Context): Intent {
            val intent = Intent(Actions.ACTION_GO_SEARCH, null, context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            return intent
        }

        fun newNavigationIntent(context: Context, item: NavigationItem): Intent {
            val intent = Intent(context, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            NavigationItem.putNavigationItem(intent, item)
            return intent
        }
    }

    fun navigateTo(navigationItem: NavigationItem) {
        val intent = when (navigationItem.type) {
            NavigationItem.Type.CUSTOMIZE_MENU -> {
                newCustomizeRootIntent()
            }
            else -> {
                newNavigationIntent(activity, navigationItem)
            }
        }
        startActivity(intent)
    }

    fun startActivity(intent: Intent) {
        activity.startActivityForResult(intent, ACTIVITY_REQUESTCODE)
    }

    fun newNowPlayingIntent(): Intent {
        // we will fill in the pendingintent on the activity side
        val newItem = NavigationItem.newFixedItem(activity, NavigationItem.Type.NOWPLAYING)
        val retval = Intent(activity, NowPlayingActivity::class.java)
        NavigationItem.putNavigationItem(retval, newItem)
        populateNavigationStack(retval)
        return retval
    }

    fun newBrowseRequestIntent(navigationItem: NavigationItem): Intent {
        val retval = newBaseBrowseIntent(null)
        NavigationItem.putNavigationItem(retval, navigationItem)
        retval.putExtra(AbsBrowseFragment.PARAM_BROWSE_STYLE, null as Parcelable?)
        return retval

    }

    fun newBrowseRequestIntent(title: String,
                               requestCommandSet: NavigationCommandSet,
                               addCommandSet: NavigationCommandSet? = null,
                               playCommandSet: NavigationCommandSet? = null,
                               playNextCommandSet: NavigationCommandSet? = null,
                               playerId: PlayerId? = null,
                               args: Bundle? = null): Intent {
        val retval = newBaseBrowseIntent(args)

        val navigationItem = NavigationItem.newBrowseRequestItem(title = title, requestCommandSet = requestCommandSet, addCommandSet = addCommandSet, playCommandSet = playCommandSet, playNextCommandSet = playNextCommandSet, playerId = playerId)
        NavigationItem.putNavigationItem(retval, navigationItem)
        retval.putExtra(AbsBrowseFragment.PARAM_BROWSE_STYLE, null as Parcelable?)
        return retval
    }

    fun newShowArtworkIntent(title: String, artworkUrl: String, args: Bundle): Intent {
        val retval = newBaseBrowseIntent(args)

        val navigationItem = NavigationItem.newBrowseArtwork(title = title, artworkUrl = artworkUrl)
        NavigationItem.putNavigationItem(retval, navigationItem)

        retval.putExtra(AbsBrowseFragment.PARAM_BROWSE_STYLE, null as Parcelable?)
        return retval
    }


    fun newShowArtworkIntent(title: String, commandSet: NavigationCommandSet, args: Bundle): Intent {
        val retval = newBaseBrowseIntent(args)

        val navigationItem = NavigationItem.newBrowseArtwork(title = title, commandSet = commandSet)
        NavigationItem.putNavigationItem(retval, navigationItem)

        retval.putExtra(AbsBrowseFragment.PARAM_BROWSE_STYLE, null as Parcelable?)
        return retval
    }

    fun newBrowseNodeIntent(title: String, browseNodeId: String, playerId: PlayerId?): Intent {
        val retval = newBaseBrowseIntent(null)

        val navigationItem = NavigationItem.newBrowseNodeItem(title = title, nodeId = browseNodeId, playerId = playerId)
        NavigationItem.putNavigationItem(retval, navigationItem)

        return retval
    }

    /**
     * launch a search for the specified query
     */
    fun createSearchIntent(query: String): Intent {

        // we will fill in the pendingintent on the activity side
        val newItem = NavigationItem.newSearchRequestItem(activity.getString(R.string.search_item_name, query), query)
        val retval = Intent(activity, SearchActivity::class.java)
        NavigationItem.putNavigationItem(retval, newItem)

        val newStack = ArrayList<NavigationItem>()
        retval.putParcelableArrayListExtra(EXTRA_NAVIGATION_STACK, newStack)
        return retval
    }

    fun createSearchTrackIntent(trackName: String): Intent {
        return createSearchIntent(trackName)
    }

    fun createSearchArtistIntent(artistName: String): Intent {
        return createSearchIntent(artistName)
    }

    fun createSearchAlbumIntent(albumName: String): Intent {
        return createSearchIntent(albumName)
    }

    private fun newCustomizeRootIntent(): Intent {
        val navigationItem = NavigationItem.newFixedItem(activity, NavigationItem.Type.CUSTOMIZE_MENU)
        val customizeIntent = Intent(activity, CustomizeRootMenuActivity::class.java)
        NavigationItem.putNavigationItem(customizeIntent, navigationItem)
        populateNavigationStack(customizeIntent)
        return customizeIntent
    }

    fun getNavigationStack(intent: Intent): List<NavigationItem> {
        return intent.getParcelableArrayListExtra(EXTRA_NAVIGATION_STACK) ?: emptyList()
    }

    private fun populateNavigationStack(intent: Intent) {
        val newStack = ArrayList(activity.navigationStack)
        newStack.add(activity.currentItem)
        intent.putParcelableArrayListExtra(EXTRA_NAVIGATION_STACK, newStack)
    }

    private fun newBaseBrowseIntent(args: Bundle?): Intent {
        val retval = Intent(activity, BrowseActivity::class.java)

        populateNavigationStack(retval)

        if (args != null) {
            retval.putExtras(args)
        }
        return retval
    }
}
