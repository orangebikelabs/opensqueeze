/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.collect.ImmutableList;

/**
 * @author tsandee
 */
public class PlayerCommands {
    final public static ImmutableList<String> NEXT_TRACK = ImmutableList.of("button", "jump_fwd");
    final public static ImmutableList<String> PREVIOUS_TRACK = ImmutableList.of("button", "jump_rew");
    final public static ImmutableList<String> PLAY = ImmutableList.of(PlayerStatus.Mode.PLAYING.getCommand());
    final public static ImmutableList<String> PAUSE = ImmutableList.of(PlayerStatus.Mode.PAUSED.getCommand(), "1");
    final public static ImmutableList<String> STOP = ImmutableList.of("stop");
    final public static ImmutableList<String> PLAYPAUSE_TOGGLE = ImmutableList.of("pause");

    public static FutureResult sendNextTrack() {
        return SBContextProvider.get().sendPlayerCommand(NEXT_TRACK);
    }

    public static FutureResult sendPreviousTrack() {
        return SBContextProvider.get().sendPlayerCommand(PREVIOUS_TRACK);
    }

    public static FutureResult sendPlay() {
        return SBContextProvider.get().sendPlayerCommand(PLAY);
    }

    public static FutureResult sendPause() {
        return SBContextProvider.get().sendPlayerCommand(PAUSE);
    }

    public static FutureResult sendStop() {
        return SBContextProvider.get().sendPlayerCommand(STOP);
    }

    public static FutureResult sendTogglePlayPause() {
        return SBContextProvider.get().sendPlayerCommand(PLAYPAUSE_TOGGLE);
    }
}
