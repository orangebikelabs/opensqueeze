/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
