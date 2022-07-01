/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.content.Context;
import androidx.fragment.app.Fragment;

import com.orangebikelabs.orangesqueeze.R;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class SyncAction extends AbsPlayerAction {
    public SyncAction(Context context) {
        super(context, R.string.action_synchronization, R.drawable.ic_sync);
    }

    @Override
    public boolean execute(Fragment controller) {
        ((ManagePlayersFragment)controller).control_synchronization(mPlayerId);
        return true;
    }
}
