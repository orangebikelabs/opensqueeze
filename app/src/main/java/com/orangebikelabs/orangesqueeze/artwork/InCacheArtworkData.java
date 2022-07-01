/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import androidx.annotation.Keep;

import com.orangebikelabs.orangesqueeze.cache.OptionalCacheValueOperations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * ArtworkCacheData implementation that represents items in the memory cache.
 *
 * @author tsandee
 */
@Keep
class InCacheArtworkData implements OptionalCacheValueOperations {

    @Nonnull
    static public InCacheArtworkData newInstance(Context context, String artworkKey, ArtworkType type, RecyclableBitmap bitmap) {
        // we're retaining a reference, bump the ref count
        bitmap.incrementRefCount();
        return new InCacheArtworkData(context, artworkKey, type, bitmap);
    }

    @GuardedBy("this")
    @Nullable
    private RecyclableBitmap mDecodedImage;

    final private long mExpectedLength;
    final private Context mContext;
    final private String mArtworkKey;
    final private ArtworkType mType;

    private InCacheArtworkData(Context context, String artworkKey, ArtworkType type, RecyclableBitmap bitmap) {
        mDecodedImage = bitmap;
        mExpectedLength = BitmapTools.bitmapSize(mDecodedImage.get());
        mArtworkKey = artworkKey;
        mContext = context;
        mType = type;
    }

    public long getEstimatedSize() {
        return mExpectedLength;
    }

    @Nonnull
    synchronized public ArtworkCacheData adaptFromMemoryCache() {
        if (mDecodedImage == null) {
            throw new IllegalStateException("already purged");
        }

        return FromCacheArtworkData.newInstance(mContext, mArtworkKey, mType, mDecodedImage);
    }

    @Override
    synchronized public void onPurgeFromMemoryCache() {
        if (mDecodedImage == null) {
            throw new IllegalStateException("already purged");
        }
        mDecodedImage.recycle();
        mDecodedImage = null;
    }
}
