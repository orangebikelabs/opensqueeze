/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;


import androidx.annotation.Keep;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
public enum ArtworkType {
    ALBUM_THUMBNAIL(true),
    ALBUM_FULL(false),

    SERVER_RESOURCE_THUMBNAIL(true),
    SERVER_RESOURCE_FULL(false),

    ARTIST_THUMBNAIL(true),
    LEGACY_ALBUM_THUMBNAIL(true);

    final private boolean mIsThumbnail;

    ArtworkType(boolean isThumbnail) {
        mIsThumbnail = isThumbnail;
    }

    public boolean isThumbnail() {
        return mIsThumbnail;
    }
}
