/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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
