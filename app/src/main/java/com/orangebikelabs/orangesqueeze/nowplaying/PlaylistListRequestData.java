/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequestData;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
public class PlaylistListRequestData extends BrowseRequestData {
    @Nonnull
    final private String mPlaylistTimestamp;

    final private int mPlaylistIndex;

    public PlaylistListRequestData(PlaylistListRequest pr) {
        super(pr);

        mPlaylistIndex = pr.getPlaylistIndex();
        mPlaylistTimestamp = pr.getPlaylistTimestamp();
    }

    @Nonnull
    public String getPlaylistTimestamp() {
        return mPlaylistTimestamp;
    }

    public int getPlaylistIndex() {
        return mPlaylistIndex;
    }
}
