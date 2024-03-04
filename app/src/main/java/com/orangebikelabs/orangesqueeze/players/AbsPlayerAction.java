/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.content.Context;

import com.orangebikelabs.orangesqueeze.actions.AbsAction;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.players.ManagePlayersAdapter.PlayerItem;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.OverridingMethodsMustInvokeSuper;

/**
 * @author tbsandee@orangebikelabs.com
 */
abstract public class AbsPlayerAction extends AbsAction<AbsPlayerItem> {

    protected PlayerId mPlayerId;

    static public List<AbsPlayerAction> getContextActionCandidates(Context context) {
        List<AbsPlayerAction> list = new ArrayList<>();

        SBContext sbContext = SBContextProvider.get();

        list.add(new SyncAction(context));
        list.add(new UnsyncAction(context));
        list.add(new RenamePlayerAction(context));
        list.add(new VolumeAction(context));
        list.add(new StopSqueezePlayerAction(context));
        list.add(new SleepAction(context));
        list.add(new MoreAction(context));

        return list;
    }

    protected AbsPlayerAction(Context context, int menuRid, int iconRid) {
        super(context.getString(menuRid), iconRid);
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    public boolean initialize(AbsPlayerItem item) {
        boolean retval = false;
        if (item instanceof PlayerItem) {
            mPlayerId = ((PlayerItem) item).getPlayerStatus().getId();
            retval = true;
        }
        return retval;
    }

}
