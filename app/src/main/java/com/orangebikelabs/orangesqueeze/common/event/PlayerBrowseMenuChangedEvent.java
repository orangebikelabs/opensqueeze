/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.common.PlayerId;

import javax.annotation.Nonnull;

/**
 * Event is triggered whenever player menus change. This can happen because a menu is updated from the server or because a menu is pinned or
 * removed from the home screen.
 *
 * @author tsandee
 */
@Keep
public class PlayerBrowseMenuChangedEvent {
    @Nonnull
    final private PlayerId mPlayerId;

    public PlayerBrowseMenuChangedEvent(PlayerId id) {
        mPlayerId = id;
    }

    @Nonnull
    public PlayerId getPlayerId() {
        return mPlayerId;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("playerId", mPlayerId).toString();
    }
}
