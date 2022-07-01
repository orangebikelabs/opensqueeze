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
public class VolumeAction extends AbsPlayerAction {

    public VolumeAction(Context context) {
        super(context, R.string.volume_desc, R.drawable.ic_volume);
    }

    @Override
    public boolean execute(Fragment controller) {
        ((ManagePlayersFragment)controller).control_volume(mPlayerId);
        return true;
    }
}
