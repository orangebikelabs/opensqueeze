/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.nowplaying

import android.app.Dialog
import android.view.View
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.google.android.material.snackbar.Snackbar
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.PlayerId
import com.orangebikelabs.orangesqueeze.common.SBContextProvider

/**
 * @author tbsandee@orangebikelabs.com
 */
object SavePlaylistDialog {
    @JvmStatic
    fun newInstance(fragment: Fragment, notifyView: View, playerId: PlayerId): Dialog {
        return MaterialDialog(fragment.requireContext()).apply {
            lifecycleOwner(fragment)
            title(res = R.string.nowplaying_saveplaylist_title)
            input(hintRes = R.string.nowplaying_saveplaylist_hint) { _, text ->
                savePlaylist(notifyView, playerId, text.toString())
            }
            positiveButton(R.string.save)
            negativeButton(R.string.cancel)
        }
    }

    /**
     * called into from saveplaylistfragment if dialog succeeds
     */
    private fun savePlaylist(notifyView: View, playerId: PlayerId, value: String) {
        SBContextProvider.get().sendPlayerCommand(playerId, "playlist", "save", value)
        val message = notifyView.resources.getString(R.string.saved_playlist_html, value)
        Snackbar.make(notifyView, HtmlCompat.fromHtml(message, 0), Snackbar.LENGTH_LONG)
                .show()
    }
}