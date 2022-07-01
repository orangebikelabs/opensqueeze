/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
            retval = pi.getSyncGroup().isPresent();
        }
        return retval;
    }

    @Override
    public boolean execute(Fragment controller) {
        ((ManagePlayersFragment)controller).control_unsync(mPlayerId);
        return true;
    }
}
