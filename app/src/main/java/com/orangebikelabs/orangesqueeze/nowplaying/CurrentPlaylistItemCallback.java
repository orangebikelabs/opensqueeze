/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import javax.annotation.Nullable;

/**
 * Callback used to communicate current playlist item between the two now playing fragments.
 */
public interface CurrentPlaylistItemCallback {
    @Nullable
    PlaylistItem getCurrentPlaylistItem();

    void setCurrentPlaylistItem(@Nullable PlaylistItem item);
}
