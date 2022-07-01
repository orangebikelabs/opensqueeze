/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerMenus;
import com.orangebikelabs.orangesqueeze.common.PlayerNotFoundException;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
abstract public class AbsContext implements SBContext {
    @Override
    public boolean isConnected() {
        return getConnectionInfo().isConnected();
    }

    @Override
    @Nonnull
    public PlayerStatus getCheckedPlayerStatus() throws PlayerNotFoundException {
        PlayerStatus status = getPlayerStatus();
        if (status == null) {
            throw new PlayerNotFoundException();
        }
        return status;
    }

    @Override
    @Nullable
    public PlayerStatus getPlayerStatus() {
        PlayerStatus retval = null;
        ServerStatus ss = getServerStatus();
        PlayerId id = getPlayerId();
        if (id != null) {
            retval = ss.getPlayerStatus(id);
        }
        return retval;
    }

    @Override
    public long getServerId() {
        ConnectionInfo ci = getConnectionInfo();
        return ci.getServerId();
    }

    @Nonnull
    @Override
    public PlayerMenus getPlayerMenus(PlayerId playerId) {
        return getServerStatus().getPlayerMenus(playerId);
    }

    @Override
    @Nonnull
    final public FutureResult sendPlayerCommand(String... commands) {
        return sendPlayerCommand(null, Arrays.asList(commands));
    }

    @Override
    @Nonnull
    final public FutureResult sendPlayerCommand(List<?> commands) {
        return sendPlayerCommand(null, commands);
    }

    @Override
    @Nonnull
    final public FutureResult sendPlayerCommand(@Nullable PlayerId playerId, String... commands) {
        return sendPlayerCommand(playerId, Arrays.asList(commands));
    }

    @Override
    @Nonnull
    final public SBRequest newRequest(String... commands) {
        return newRequest(Arrays.asList(commands));
    }

    @Nonnull
    @Override
    final public SBRequest newRequest(SBRequest.Type requestType, String... commands) {
        return newRequest(requestType, Arrays.asList(commands));
    }
}
