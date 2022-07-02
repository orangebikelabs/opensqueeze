/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui;

import android.app.Dialog;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.SBDialogFragment;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Allows user to choose a new player, which is then activated.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class ChoosePlayerDialogFragment extends SBDialogFragment {
    public static ChoosePlayerDialogFragment newInstance() {
        return new ChoosePlayerDialogFragment();
    }

    @Override
    @Nonnull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.select_player_title)
                .setCancelable(true)
                .setIcon(android.R.drawable.ic_dialog_info);

        PlayerStatus currentPlayer = mContext.getPlayerStatus();

        int selectedIndex = 0;
        final List<PlayerStatus> playerList = new ArrayList<>(mContext.getServerStatus().getAvailablePlayers());

        ArrayList<String> players = new ArrayList<>();
        for (PlayerStatus s : mContext.getServerStatus().getAvailablePlayers()) {
            if (s == currentPlayer) {
                selectedIndex = players.size();
            }
            players.add(s.getName());
        }
        builder.setSingleChoiceItems(players.toArray(new String[0]), selectedIndex, (dialog, which) -> {
            PlayerStatus s = playerList.get(which);
            mContext.setPlayerById(s.getId());
            dismiss();
        });
        builder.setNegativeButton(R.string.cancel, (dialog, whichButton) -> {
            // nothing to do
        });

        Dialog retval = builder.create();
        retval.setCanceledOnTouchOutside(true);
        return retval;
    }
}
