/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.common.io.ByteSource;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.ThreadTools;

import java.io.Closeable;
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This is the interface that is applied to artwork data after is retrieved from storage or when it is stored in memory.
 * <p/>
 * The default implementation for artwork data is to first check the decoded bitmap cache for a RecyclableBitmap object, otherwise the
 * decodeBitmap() method will perform the decode.
 *
 * @author tsandee
 */

abstract public class ArtworkCacheData implements Closeable {

    public enum ImageTarget {
        BITMAP, DATABASE
    }

    @Nonnull
    final protected Context mApplicationContext;

    @Nonnull
    final protected String mArtworkKey;

    @Nonnull
    final protected ArtworkType mType;

    protected ArtworkCacheData(Context context, String artworkKey, ArtworkType type) {
        OSAssert.assertApplicationContext(context);

        mApplicationContext = context;
        mArtworkKey = artworkKey;
        mType = type;
    }

    @Nonnull
    public ArtworkType getType() {
        return mType;
    }

    /**
     * This decodes the bitmap and returns it.
     * <p/>
     *
     * @return null if called on the main thread and bitmap is not already decoded.
     */
    @Nullable
    public RecyclableBitmap decodeBitmap() throws IOException {
        if (ThreadTools.isMainThread()) {
            return null;
        }

        OSLog.TimingLoggerCompat timing = OSLog.Tag.ARTWORK.newTimingLogger("decodeBitmap");

        // the recyclable bitmap will have refcount=1 for the caller
        // we don't hold onto a reference to the image directly
        ByteSource bytes = getImageByteSource(ImageTarget.BITMAP);
        timing.addSplit("image bytes retrieved");

        BitmapDecoder decoder = BitmapDecoder.getInstance(mApplicationContext, mType);
        RecyclableBitmap retval = decoder.decodeScaledBitmapForDeviceDisplay(bytes, Bitmap.Config.ARGB_8888);
        timing.addSplit("decoded");

        timing.close();

        return retval;
    }

    /**
     * Retrieve the estimated PERSISTENT size of the artwork as it is stored on disk.
     */
    abstract public long getEstimatedSize();

    /**
     * Typically called by the cache layer to get the persistent storage representation of the artwork (PNG-encoded image data)
     */
    @Nonnull
    abstract public ByteSource getImageByteSource(ImageTarget target) throws IOException;

    @Nonnull
    public InCacheArtworkData adaptForMemoryCache() throws IOException {
        RecyclableBitmap bmp = decodeBitmap();
        OSAssert.assertNotNull(bmp, "bitmap should never be null");

        try {
            return InCacheArtworkData.newInstance(mApplicationContext, mArtworkKey, mType, bmp);
        } finally {
            bmp.recycle();
        }
    }
}
