/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;
import arrow.core.Option;

import com.google.common.base.MoreObjects;
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
    public Option<PlayerStatus> getPlayerStatus() {
        return Option.fromNullable(mPlayerStatus);
    }

    @Nonnull
    public Option<PlayerId> getPlayerId() {
        PlayerId retval = null;
        if (mPlayerStatus != null) {
            retval = mPlayerStatus.getId();
        }
        return Option.fromNullable(retval);
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("playerStatus", mPlayerStatus).toString();
    }
}
