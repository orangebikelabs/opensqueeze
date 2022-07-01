/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.Bitmap;

import com.orangebikelabs.orangesqueeze.cache.CachedItemInvalidException;
import com.orangebikelabs.orangesqueeze.cache.ManagedTemporary;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;

import java.io.IOException;

import javax.annotation.Nonnull;

/**
 * Artwork cache data the is used for data just retrieved remotely. It stores both a new rescaled bitmap as well as a managed temporary with the encoded data.
 */
public class ScalingArtworkData extends ManagedTemporaryArtworkCacheData {
    final private RecyclableBitmap mBitmap;

    public ScalingArtworkData(Context context, String artworkKey, ArtworkType type, int pixelWidth, ManagedTemporary managedTemporary) throws IOException,
            CachedItemInvalidException {
        super(context, artworkKey, type, managedTemporary);

        // ensure we aren't decoding images on the main thread...
        OSAssert.assertNotMainThread();

        OSLog.TimingLoggerCompat logger = OSLog.Tag.ARTWORK.newTimingLogger("Rescale remote artwork for display and storage (request "
                + ArtworkRequestId.next() + ")");

        // create custom decoder that disables bitmap recycling
        BitmapDecoder decoder = BitmapDecoder.getInstance(context);

        @SuppressWarnings({"UnnecessaryLocalVariable", "SuspiciousNameCombination"}) final int height = pixelWidth;
        // first, create bitmap that's possibly quite a bit too large but
        // good quality and small enough to load
        RecyclableBitmap original = decoder.decodeScaledBitmapInexact(mManagedTemporary.asByteSource(), Bitmap.Config.ARGB_8888, pixelWidth * 2, height * 2);

        logger.addSplit("decoded, initial size: " + mManagedTemporary.size());
        Bitmap bmp;
        if (mType.isThumbnail()) {
            // for thumbnails, resize them down to exact size
            // we've got the bitmap but it's too big, scale it
            // exactly. This may, or may not, create a new bitmap.
            Bitmap newExact = BitmapTools.createScaledBitmap(original.get(), pixelWidth, height);
            if (newExact == null) {
                throw new CachedItemInvalidException("Error scaling artwork");
            }
            // did it create a new bitmap?
            if (newExact != original.get()) {
                // get rid of the big honker explicitly
                original.get().recycle();
            }
            bmp = newExact;
        } else {
            bmp = original.get();
        }

        BitmapTools.compress(type, bmp, managedTemporary.asByteSink());
        logger.addSplit("compressed, new size: " + managedTemporary.size());
        logger.close();

        mBitmap = RecyclableBitmap.newNonRecyclableInstance(bmp);
    }

    @Override
    @Nonnull
    public RecyclableBitmap decodeBitmap() {
        return mBitmap;
    }

    @Override
    public void close() throws IOException {
        super.close();

        mBitmap.recycle();
    }
}
