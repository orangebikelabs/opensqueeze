/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
