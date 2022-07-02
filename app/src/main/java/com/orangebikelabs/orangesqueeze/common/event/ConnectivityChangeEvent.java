/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
