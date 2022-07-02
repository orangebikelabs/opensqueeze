/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;


/**
 * @author tbsandee@orangebikelabs.com
 */
public class PlaylistActionInfo {
    final public int mPosition;
    final public PlaylistItem mItem;

    public PlaylistActionInfo(int position, PlaylistItem item) {
        mPosition = position;
        mItem = item;
    }
}
