/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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
