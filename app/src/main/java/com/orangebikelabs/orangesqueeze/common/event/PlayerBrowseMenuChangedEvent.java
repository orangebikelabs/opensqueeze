/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
