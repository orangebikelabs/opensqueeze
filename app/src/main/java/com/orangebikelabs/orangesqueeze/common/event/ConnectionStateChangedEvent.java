/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;

import javax.annotation.Nonnull;

/**
 * Broadcast with the current connection state. This is a produced event.
 *
 * @author tsandee
 */
@Keep
public class ConnectionStateChangedEvent {

    @Nonnull
    final private ConnectionInfo mConnectionInfo;

    public ConnectionStateChangedEvent(ConnectionInfo ci) {
        mConnectionInfo = ci;
    }

    @Nonnull
    public ConnectionInfo getConnectionInfo() {
        return mConnectionInfo;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("connectionInfo", mConnectionInfo).toString();
    }
}
