/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.content.Context;
import androidx.fragment.app.Fragment;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.players.ManagePlayersAdapter.PlayerItem;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class ConnectToSNAction extends AbsPlayerAction {

    public ConnectToSNAction(Context context) {
        super(context, R.string.action_squeezenetwork_connect_player, R.drawable.ic_cloud);
    }

    @Override
    public boolean execute(Fragment controller) {
        ((ManagePlayersFragment)controller).control_connectToSqueezeNetwork(mPlayerId);
        return true;
    }

    @Override
    public boolean initialize(AbsPlayerItem item) {
        boolean applies = super.initialize(item);
        if (applies) {
            PlayerItem pi = (PlayerItem) item;
            applies = pi.getPlayerStatus().isSqueezenetworkCapable();
        }
        return applies;
    }
}
