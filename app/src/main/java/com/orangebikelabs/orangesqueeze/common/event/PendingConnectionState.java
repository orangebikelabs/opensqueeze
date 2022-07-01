/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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
