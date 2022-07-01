/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */
package com.orangebikelabs.orangesqueeze.players

import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.PlayerStatus
import javax.annotation.Nonnull

/**
 * @author tbsandee@orangebikelabs.com
 */
object RenamePlayerDialog {
    @JvmStatic
    @Nonnull
    fun newInstance(fragment: ManagePlayersFragment, playerStatus: PlayerStatus): MaterialDialog {

        val context = fragment.requireContext()
        val playerName = playerStatus.name
        val playerId = playerStatus.id

        return MaterialDialog(context).apply {
            lifecycleOwner(fragment)
            title(R.string.rename_player_title)
            cancelable(true)
            input(hint = "Player Name", prefill = playerName, allowEmpty = false) { _, text ->
                // do nothing
                fragment.control_performPlayerRename(playerId, text.toString())
            }
            positiveButton(res = R.string.ok)
            negativeButton(res = R.string.cancel)
        }
    }
}