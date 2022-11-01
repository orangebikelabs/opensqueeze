/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import com.orangebikelabs.orangesqueeze.BuildConfig
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.compat.getParcelableCompat
import kotlinx.parcelize.Parcelize

@Parcelize
data class NavigationItem(
        val name: String,
        val type: Type,
        val nodeId: String? = null,
        val artworkUrl: String? = null,
        val searchQuery: String? = null,
        val addCommandSet: NavigationCommandSet? = null,
        val playCommandSet: NavigationCommandSet? = null,
        val playNextCommandSet: NavigationCommandSet? = null,
        val requestCommandSet: NavigationCommandSet? = null,
        val clearBackstack: Boolean = false,
        val playerId: PlayerId? = null
) : Parcelable {

    enum class Type constructor(val titleRid: Int, val iconRid: Int, val clearBackstack: Boolean) {
        DOWNLOADS(R.string.navigation_downloads, R.drawable.ic_download, true),
        PLAYERS(R.string.navigation_players, R.drawable.ic_boombox, true),
        NOWPLAYING(R.string.navigation_nowplaying, 0, false),
        CUSTOMIZE_MENU(R.string.navigation_customize, R.drawable.ic_reorder, false),
        HOME(R.string.navigation_browse, 0, true),
        SEARCHREQUEST(0, 0, true),
        BROWSEREQUEST(0, 0, false),
        BROWSENODE(0, 0, false),
        BROWSEARTWORK(0, 0, false),
        PREPAREDOWNLOAD(0, 0, false)
    }

    companion object {
        private const val EXTRA_NAVIGATIONITEM = BuildConfig.APPLICATION_ID + ".extra.navigationItem"

        fun putNavigationItem(intent: Intent, item: NavigationItem) {
            intent.putExtra(EXTRA_NAVIGATIONITEM, item)
        }

        fun putNavigationItem(bundle: Bundle, item: NavigationItem) {
            bundle.putParcelable(EXTRA_NAVIGATIONITEM, item)
        }

        fun getNavigationItem(intent: Intent?): NavigationItem? {
            return getNavigationItem(intent?.extras)
        }

        fun getNavigationItem(bundle: Bundle?): NavigationItem? {
            var retval: NavigationItem? = null
            if (bundle != null) {
                retval = bundle.getParcelableCompat(EXTRA_NAVIGATIONITEM, NavigationItem::class.java)
            }
            return retval
        }

        fun removeNavigationItem(bundle: Bundle) {
            bundle.remove(EXTRA_NAVIGATIONITEM)
        }

        fun newBrowseArtistItem(artistId: String, artistTitle: String): NavigationItem {
            val requestCommandSet: NavigationCommandSet
            var addCommandSet: NavigationCommandSet? = null
            var playCommandSet: NavigationCommandSet? = null

            if (SBContextProvider.get().serverStatus.version < "7.6.0") {
                requestCommandSet = NavigationCommandSet(
                        "albums", "menu_all:1", "menu:track",
                        "useContextMenu:1", "artist_id:$artistId"
                )
            } else {
                requestCommandSet = NavigationCommandSet(
                        listOf("browselibrary", "items"),
                        "mode:albums", "menu:1", "artist_id:$artistId", "useContextMenu:1"
                )
                addCommandSet = NavigationCommandSet(
                        "playlistcontrol", "artist_id:$artistId", "cmd:add",
                        "menu:1"
                )
                playCommandSet = NavigationCommandSet(
                        "playlistcontrol", "artist_id:$artistId", "cmd:load",
                        "menu:1"
                )
            }
            return NavigationItem(
                    type = Type.BROWSEREQUEST,
                    name = artistTitle,
                    requestCommandSet = requestCommandSet,
                    addCommandSet = addCommandSet,
                    playCommandSet = playCommandSet
            )
        }

        fun newBrowseAlbumItem(albumId: String, albumTitle: String): NavigationItem {
            val requestCommandSet: NavigationCommandSet
            var addCommandSet: NavigationCommandSet? = null
            var playCommandSet: NavigationCommandSet? = null

            if (SBContextProvider.get().serverStatus.version < "7.6.0") {
                requestCommandSet = NavigationCommandSet(
                        "tracks", "menu_all:1", "sort:tracknum",
                        "menu:trackinfo", "useContextMenu:1", "album_id:$albumId"
                )
            } else {
                requestCommandSet = NavigationCommandSet(
                        listOf("browselibrary", "items"),
                        "mode:tracks", "menu:1", "album_id:$albumId", "useContextMenu:1"
                )
                addCommandSet = NavigationCommandSet(
                        "playlistcontrol", "album_id:$albumId", "cmd:add",
                        "menu:1"
                )
                playCommandSet = NavigationCommandSet(
                        "playlistcontrol", "album_id:$albumId", "cmd:load",
                        "menu:1"
                )
            }
            return NavigationItem(
                    type = Type.BROWSEREQUEST, name = albumTitle,
                    requestCommandSet = requestCommandSet,
                    addCommandSet = addCommandSet,
                    playCommandSet = playCommandSet
            )
        }

        @Suppress("unused")
        fun newBrowseYear(year: String): NavigationItem {
            val requestCommandSet: NavigationCommandSet
            var addCommandSet: NavigationCommandSet? = null
            var playCommandSet: NavigationCommandSet? = null

            if (SBContextProvider.get().serverStatus.version < "7.6.0") {
                requestCommandSet = NavigationCommandSet(
                        "albums", "menu_all:1", "menu:track",
                        "useContextMenu:1", "year:$year"
                )
            } else {
                requestCommandSet = NavigationCommandSet(
                        listOf("browselibrary", "items"),
                        "mode:albums", "menu:1", "year:$year", "useContextMenu:1"
                )
                addCommandSet = NavigationCommandSet("playlistcontrol", "year:$year", "cmd:add", "menu:1")
                playCommandSet = NavigationCommandSet("playlistcontrol", "year:$year", "cmd:load", "menu:1")
            }
            return NavigationItem(
                    type = Type.BROWSEREQUEST, name = year,
                    requestCommandSet = requestCommandSet,
                    addCommandSet = addCommandSet,
                    playCommandSet = playCommandSet
            )
        }

        @Suppress("unused")
        fun newBrowseGenre(genreId: String, genre: String): NavigationItem {
            val requestCommandSet: NavigationCommandSet
            var addCommandSet: NavigationCommandSet? = null
            var playCommandSet: NavigationCommandSet? = null

            if (SBContextProvider.get().serverStatus.version < "7.6.0") {
                requestCommandSet = NavigationCommandSet(
                        "albums", "menu_all:1", "menu:track",
                        "useContextMenu:1", "genre_id:$genreId"
                )
            } else {
                requestCommandSet = NavigationCommandSet(
                        listOf("browselibrary", "items"),
                        "mode:albums", "menu:1", "genre_id:$genreId", "useContextMenu:1"
                )
                addCommandSet = NavigationCommandSet(
                        "playlistcontrol", "genre_id:$genreId", "cmd:add",
                        "menu:1"
                )
                playCommandSet = NavigationCommandSet(
                        "playlistcontrol", "genre_id:$genreId", "cmd:load",
                        "menu:1"
                )
            }
            return NavigationItem(
                    type = Type.BROWSEREQUEST, name = genre,
                    requestCommandSet = requestCommandSet,
                    addCommandSet = addCommandSet,
                    playCommandSet = playCommandSet
            )
        }

        fun newBrowseArtwork(title: String, artworkUrl: String): NavigationItem {
            return NavigationItem(name = title, type = Type.BROWSEARTWORK, artworkUrl = artworkUrl)
        }

        fun newBrowseArtwork(title: String, commandSet: NavigationCommandSet): NavigationItem {
            return NavigationItem(name = title, type = Type.BROWSEARTWORK, requestCommandSet = commandSet)
        }

        fun newBrowseRequestItem(
                title: String,
                requestCommandSet: NavigationCommandSet,
                nodeId: String? = null,
                addCommandSet: NavigationCommandSet? = null,
                playCommandSet: NavigationCommandSet? = null,
                playNextCommandSet: NavigationCommandSet? = null,
                clearBackstack: Boolean = false,
                playerId: PlayerId? = null
        ): NavigationItem {
            return NavigationItem(
                    type = Type.BROWSEREQUEST,
                    name = title,
                    nodeId = nodeId,
                    requestCommandSet = requestCommandSet,
                    addCommandSet = addCommandSet,
                    playCommandSet = playCommandSet,
                    playNextCommandSet = playNextCommandSet,
                    clearBackstack = clearBackstack,
                    playerId = playerId
            )
        }

        fun newSearchRequestItem(title: String, query: String): NavigationItem {
            return NavigationItem(type = Type.SEARCHREQUEST, name = title, searchQuery = query)
        }

        fun newPrepareDownloadItem(title: String, requestCommandSet: NavigationCommandSet): NavigationItem {
            return NavigationItem(type = Type.PREPAREDOWNLOAD, name = title, requestCommandSet = requestCommandSet)
        }

        fun newBrowseNodeItem(title: String, nodeId: String, clearBackstack: Boolean = false, playerId: PlayerId? = null): NavigationItem {
            return NavigationItem(type = Type.BROWSENODE, name = title, nodeId = nodeId, clearBackstack = clearBackstack, playerId = playerId)
        }

        fun newFixedItem(context: Context, type: Type): NavigationItem {
            return NavigationItem(type = type, name = context.getString(type.titleRid), clearBackstack = type.clearBackstack)
        }
    }
}
