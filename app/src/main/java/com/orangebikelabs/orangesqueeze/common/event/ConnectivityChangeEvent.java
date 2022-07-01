/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
@Keep
public class ConnectivityChangeEvent {
    final private boolean mConnectivity;

    public ConnectivityChangeEvent(boolean connectivity) {
        mConnectivity = connectivity;
    }

    public boolean hasConnectivity() {
        return mConnectivity;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("connectivity", mConnectivity).toString();
    }
}
