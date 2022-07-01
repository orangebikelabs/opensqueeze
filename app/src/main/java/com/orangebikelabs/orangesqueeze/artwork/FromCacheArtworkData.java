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
import com.orangebikelabs.orangesqueeze.common.Closeables;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * ArtworkCacheData implementation that gets its data from cached binary data
 *
 * @author tsandee
 */
class FromCacheArtworkData extends ArtworkCacheData {

    @Nonnull
    static public ArtworkCacheData newInstance(Context context, String artworkKey, ArtworkType type, RecyclableBitmap bitmap) {
        // we're retaining a reference, bump the ref count
        bitmap.incrementRefCount();
        return new FromCacheArtworkData(context, artworkKey, type, bitmap);
    }

    @Nonnull
    static public ArtworkCacheData newInstance(Context context, String artworkKey, ArtworkType type, ManagedTemporary temporary) throws IOException {
        return newInstance(context, artworkKey, type, temporary.asByteSource());
    }

    @Nonnull
    static public ArtworkCacheData newInstance(Context context, String artworkKey, ArtworkType type, ByteSource byteSource) throws IOException {
        BitmapDecoder decoder = BitmapDecoder.getInstance(context, type);
        RecyclableBitmap bmp = decoder.decodeScaledBitmapForDeviceDisplay(byteSource, Bitmap.Config.ARGB_8888);
        return new FromCacheArtworkData(context, artworkKey, type, bmp);
    }

    @GuardedBy("this")
    @Nullable
    private RecyclableBitmap mDecodedImage;

    final private long mExpectedLength;

    @GuardedBy("this")
    @Nullable
    protected ManagedTemporary mManagedTemporary;

    private FromCacheArtworkData(Context context, String artworkKey, ArtworkType type, RecyclableBitmap bitmap) {
        super(context, artworkKey, type);

        mDecodedImage = bitmap;
        mExpectedLength = BitmapTools.bitmapSize(mDecodedImage.get());
    }

    @Override
    public long getEstimatedSize() {
        return mExpectedLength;
    }

    @Override
    @Nonnull
    synchronized public ByteSource getImageByteSource(ImageTarget target) throws IOException {
        if (mDecodedImage == null) {
            throw new IllegalStateException("closed");
        }
        if (mManagedTemporary == null) {
            mManagedTemporary = CacheServiceProvider.get().createManagedTemporary();
        }
        // now reencode
        BitmapTools.compress(mType, mDecodedImage.get(), mManagedTemporary.asByteSink());
        return mManagedTemporary.asByteSource();
    }

    @Nonnull
    @Override
    synchronized public RecyclableBitmap decodeBitmap() {
        if (mDecodedImage == null) {
            throw new IllegalStateException("closed");
        }
        mDecodedImage.incrementRefCount();
        return mDecodedImage;
    }

    @Override
    synchronized public void close() {
        if (mDecodedImage == null) {
            throw new IllegalStateException("closed");
        }
        try {
            Closeables.close(mManagedTemporary);
        } catch (IOException e) {
            // ignore
        }
        mDecodedImage.recycle();
        mDecodedImage = null;
    }
}
