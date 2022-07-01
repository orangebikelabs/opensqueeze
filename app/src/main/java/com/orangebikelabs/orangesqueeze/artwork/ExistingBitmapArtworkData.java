/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.Bitmap;

import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.common.OSAssert;

import java.io.IOException;

/**
 * Used to return artwork data when it was built from a Bitmap object dynamically. (e.g. Artist Thumbnail Artwork)
 *
 * @author tsandee
 */
public class ExistingBitmapArtworkData extends ManagedTemporaryArtworkCacheData {
    public ExistingBitmapArtworkData(Context context, String artworkKey, ArtworkType type, Bitmap bitmap) throws IOException {
        super(context, artworkKey, type, CacheServiceProvider.get().createManagedTemporary());

        // ensure we aren't accidentally compressing images on the main thread...
        OSAssert.assertNotMainThread();

        boolean success = false;
        try {
            BitmapTools.compress(type, bitmap, mManagedTemporary.asByteSink());
            success = true;
        } finally {
            if (!success) {
                mManagedTemporary.close();
            }
        }
    }
}