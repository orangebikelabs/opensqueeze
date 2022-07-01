/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.app.PendingConnection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
@Keep
public class PendingConnectionState {
    @Nullable
    final private PendingConnection mPendingConnection;

    final private boolean mConnecting;

    public PendingConnectionState(boolean connecting, @Nullable PendingConnection pendingConnection) {
        mConnecting = connecting;
        mPendingConnection = pendingConnection;
    }

    public boolean isConnecting() {
        return mConnecting;
    }

    @Nullable
    public PendingConnection getPendingConnection() {
        return mPendingConnection;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("connecting", mConnecting).add("pending", mPendingConnection).toString();
    }
}
