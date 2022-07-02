/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import javax.annotation.Nullable;

/**
 * Exception thrown when a player is not found when it is expected.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class PlayerNotFoundException extends SBRequestException {

    @Nullable
    final private PlayerId mPlayerId;

    public PlayerNotFoundException() {
        super("No players found");
        mPlayerId = null;
    }

    public PlayerNotFoundException(PlayerId playerId) {
        super("Player Not Found: " + playerId);
        mPlayerId = playerId;
    }

    @Nullable
    public PlayerId getPlayerId() {
        return mPlayerId;
    }
}
