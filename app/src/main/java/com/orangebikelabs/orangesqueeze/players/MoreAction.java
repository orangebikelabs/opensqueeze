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
public class MoreAction extends AbsPlayerAction {


    public MoreAction(Context context) {
        super(context, R.string.action_moreplayermenu, R.drawable.ic_more);
    }

    @Override
    public boolean execute(Fragment controller) {
        ((ManagePlayersFragment)controller).control_browseMoreMenu(mPlayerId);
        return true;
    }
}
