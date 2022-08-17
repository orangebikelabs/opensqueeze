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
public class UnsyncAction extends AbsPlayerAction {

    public UnsyncAction(Context context) {
        super(context, R.string.action_unsync, R.drawable.ic_sync_disabled);
    }

    @Override
    public boolean initialize(AbsPlayerItem item) {
        boolean retval = super.initialize(item);
        if (retval) {
            PlayerItem pi = (PlayerItem) item;
            retval = pi.getSyncGroup().isDefined();
        }
        return retval;
    }

    @Override
    public boolean execute(Fragment controller) {
        ((ManagePlayersFragment)controller).control_unsync(mPlayerId);
        return true;
    }
}
