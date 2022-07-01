/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;

import com.google.common.io.ByteSource;
import com.orangebikelabs.orangesqueeze.cache.ManagedTemporary;

import java.io.IOException;

import javax.annotation.Nonnull;

/**
 * Artwork cache data that is backed by a managed temporary object. These cannot be stored in the memory cache directly so they must be adapted.
 */
abstract public class ManagedTemporaryArtworkCacheData extends ArtworkCacheData {
    @Nonnull
    final protected ManagedTemporary mManagedTemporary;

    public ManagedTemporaryArtworkCacheData(Context context, String artworkKey, ArtworkType type, ManagedTemporary managedTemporary) {
        super(context, artworkKey, type);

        mManagedTemporary = managedTemporary;
    }

    @Override
    public long getEstimatedSize() {
        return mManagedTemporary.size();
    }

    @Override
    @Nonnull
    public ByteSource getImageByteSource(ImageTarget target) throws IOException {
        return mManagedTemporary.asByteSource();
    }

    @Override
    public void close() throws IOException {
        mManagedTemporary.close();
    }
}
