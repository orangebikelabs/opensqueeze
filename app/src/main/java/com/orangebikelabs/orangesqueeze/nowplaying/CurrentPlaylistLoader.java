/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import com.orangebikelabs.orangesqueeze.common.LoopingRequestLoader;

/**
 * @author tsandee
 */
public class CurrentPlaylistLoader extends LoopingRequestLoader<PlaylistListRequestData> {
    public CurrentPlaylistLoader() {
        super(new PlaylistListRequest());
    }
}
