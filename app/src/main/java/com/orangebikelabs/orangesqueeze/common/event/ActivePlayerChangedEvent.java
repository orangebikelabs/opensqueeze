/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
@Keep
public class ActivePlayerChangedEvent {
    @Nullable
    final private PlayerStatus mPlayerStatus;

    public ActivePlayerChangedEvent(@Nullable PlayerStatus playerStatus) {
        mPlayerStatus = playerStatus;
    }

    @Nonnull
    public Optional<PlayerStatus> getPlayerStatus() {
        return Optional.fromNullable(mPlayerStatus);
    }

    @Nonnull
    public Optional<PlayerId> getPlayerId() {
        PlayerId retval = null;
        if (mPlayerStatus != null) {
            retval = mPlayerStatus.getId();
        }
        return Optional.fromNullable(retval);
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("playerStatus", mPlayerStatus).toString();
    }
}
