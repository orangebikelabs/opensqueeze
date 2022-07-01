/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
@Keep
public class AnyPlayerStatusEvent {
    @Nonnull
    final private PlayerStatus mNewStatus;

    @Nullable
    final private PlayerStatus mOldStatus;

    public AnyPlayerStatusEvent(PlayerStatus newStatus, @Nullable PlayerStatus oldStatus) {
        mNewStatus = newStatus;
        mOldStatus = oldStatus;
    }

    @Nonnull
    public PlayerStatus getPlayerStatus() {
        return mNewStatus;
    }


    @Nullable
    public PlayerStatus getPreviousPlayerStatus() {
        return mOldStatus;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("playerStatus", mNewStatus).toString();
    }
}
