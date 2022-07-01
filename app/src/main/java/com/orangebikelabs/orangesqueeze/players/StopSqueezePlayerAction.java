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
public class StopSqueezePlayerAction extends AbsPlayerAction {

    public StopSqueezePlayerAction(Context context) {
        super(context, R.string.action_squeezeplayer_stop, R.drawable.ic_clear);
    }

    @Override
    public boolean initialize(AbsPlayerItem item) {
        boolean retval = super.initialize(item);
        if (retval) {
            PlayerItem pi = (PlayerItem) item;
            retval = pi.getPlayerStatus().isLocalSqueezePlayer();
        }
        return retval;
    }

    @Override
    public boolean execute(Fragment controller) {
        ((ManagePlayersFragment)controller).control_stopSqueezePlayer(mPlayerId);
        return true;
    }
}
