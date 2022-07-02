/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
