/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.Bitmap;

import com.google.common.io.ByteSource;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.cache.ManagedTemporary;
import com.orangebikelabs.orangesqueeze.common.OSLog;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

/**
 * Recompress artwork data obtained from other sources.
 */
public class RecompressArtworkData extends ManagedTemporaryArtworkCacheData {

    @GuardedBy("this")
    private ManagedTemporary mResizedManagedTemporary;

    @GuardedBy("this")
    private ManagedTemporary mReturnedManagedTemporary;

    public RecompressArtworkData(Context context, String artworkKey, ArtworkType type, ManagedTemporary managedTemporary) {
        super(context, artworkKey, type, managedTemporary);
    }

    @Override
    @Nonnull
    public ByteSource getImageByteSource(ImageTarget target) throws IOException {
        if (target == ImageTarget.DATABASE) {
            synchronized (this) {
                if (mReturnedManagedTemporary == null) {
                    mResizedManagedTemporary = CacheServiceProvider.get().createManagedTemporary();

                    OSLog.TimingLoggerCompat logger = OSLog.Tag.ARTWORK.newTimingLogger("Recompress artwork for storage (request " + ArtworkRequestId.next()
                            + ")");
                    logger.addSplit("original size: " + mManagedTemporary.size());

                    BitmapDecoder decoder = BitmapDecoder.getInstance(mApplicationContext);

                    Bitmap bmp;
                    try {
                        bmp = decoder.decode(mManagedTemporary.asByteSource());
                    } catch (OutOfMemoryError e) {
                        OSLog.i(OSLog.Tag.ARTWORK, "OOM, trying to rescale artwork based on screen dimensions");
                        bmp = decoder.decodeScaledBitmapForDeviceDisplay(mManagedTemporary.asByteSource(), Bitmap.Config.ARGB_8888).get();
                    }
                    logger.addSplit("decoded");

                    // now reencode
                    BitmapTools.compress(mType, bmp, mResizedManagedTemporary.asByteSink());
                    bmp.recycle();
                    logger.addSplit("compressed size: " + mResizedManagedTemporary.size());
                    logger.close();

                    if (mResizedManagedTemporary.size() > mManagedTemporary.size()) {
                        mReturnedManagedTemporary = mManagedTemporary;

                        // abandon resize
                        mResizedManagedTemporary.close();
                        mResizedManagedTemporary = null;
                    } else {
                        mReturnedManagedTemporary = mResizedManagedTemporary;
                    }
                }
                return mReturnedManagedTemporary.asByteSource();
            }
        } else {
            return super.getImageByteSource(target);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();

        synchronized (this) {
            if (mResizedManagedTemporary != null) {
                mResizedManagedTemporary.close();
                mResizedManagedTemporary = null;
            }
        }
    }
}
