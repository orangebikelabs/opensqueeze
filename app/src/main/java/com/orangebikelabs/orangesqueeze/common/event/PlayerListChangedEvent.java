/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
@Keep
public class PlayerListChangedEvent {

    @Nonnull
    final private List<PlayerStatus> mPlayerStatusLists;

    public PlayerListChangedEvent(ServerStatus serverStatus) {
        mPlayerStatusLists = new ArrayList<>(serverStatus.getAvailablePlayers());
    }

    @Nonnull
    public List<PlayerStatus> getPlayerStatusList() {
        return mPlayerStatusLists;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("playerList", mPlayerStatusLists).toString();
    }
}
