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
public class SleepAction extends AbsPlayerAction {


    public SleepAction(Context context) {
        super(context, R.string.action_sleepplayer, R.drawable.ic_snooze);
    }

    @Override
    public boolean execute(Fragment controller) {
        ((ManagePlayersFragment)controller).control_showSleepPlayerDialog(mPlayerId);
        return true;
    }
}
