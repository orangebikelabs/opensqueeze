/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
@Keep
public class CurrentServerState {

    @Nonnull
    final private ServerStatus mServerStatus;

    public CurrentServerState(ServerStatus serverStatus) {
        mServerStatus = serverStatus;
    }

    @Nonnull
    public ServerStatus getServerStatus() {
        return mServerStatus;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("serverStatus", mServerStatus).toString();
    }
}
