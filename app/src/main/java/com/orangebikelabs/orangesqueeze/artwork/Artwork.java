/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.annotation.Keep;
import android.util.DisplayMetrics;
import android.util.SparseArray;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.cache.CacheFuture;
import com.orangebikelabs.orangesqueeze.cache.CacheRequestCallback;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.cache.CachedItemInvalidException;
import com.orangebikelabs.orangesqueeze.cache.CachedItemNotFoundException;
import com.orangebikelabs.orangesqueeze.cache.SBCacheException;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerId;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * @author tsandee
 */
public class Artwork {
    static final private Artwork sMissing = new Artwork();
    static final private AtomicLong sFutureRequestGenerator = new AtomicLong();
    static final private ConcurrentMap<ListenableFuture<Bitmap>, Long> sFutureRequestMap = new MapMaker().concurrencyLevel(1).weakKeys().makeMap();

    static public Artwork missing() {
        return sMissing;
    }

    static public boolean equivalent(@Nullable ListenableFuture<Bitmap> f1, @Nullable ListenableFuture<Bitmap> f2) {
        if (f1 == f2) {
            return true;
        }
        if (f1 == null || f2 == null) {
            return false;
        }

        Long v1 = sFutureRequestMap.get(f1);
        if (v1 == null) {
            return false;
        }

        Long v2 = sFutureRequestMap.get(f2);

        return Objects.equal(v1, v2);
    }

    @Nonnull
    static public Artwork getInstance(Context context, PlayerId playerId, @Nullable String id, @Nullable String url) {
        if (id == null && url == null) {
            return sMissing;
        }

        return new ArtworkImpl(context, playerId, id, url);
    }

    public static int getFullSizeArtworkWidth(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();

        return 2 * Math.min(dm.heightPixels, dm.widthPixels);
    }

    @Nonnull
    static public ArtworkCacheRequestCallback newCacheRequest(Context context, String id, ArtworkType artworkType, int pixelWidth) {
        StandardArtworkCacheRequest request;
        Context applicationContext = context.getApplicationContext();
        OSAssert.assertNotNull(applicationContext, "application context should not be null");

        if (artworkType == ArtworkType.ARTIST_THUMBNAIL) {
            request = new ArtistThumbnailCacheRequest(applicationContext, id, pixelWidth);
        } else if (artworkType == ArtworkType.LEGACY_ALBUM_THUMBNAIL) {
            request = new LegacyAlbumThumbnailCacheRequest(applicationContext, id, pixelWidth);
        } else {
            request = new StandardArtworkCacheRequest(applicationContext, id, artworkType, pixelWidth);

        }
        return request;
    }

    public boolean isEquivalent(@Nullable String id, @Nullable String url) {
        return id == null && url == null;
    }

    public boolean isPresent() {
        return false;
    }

    final static private ListenableFuture<Bitmap> sEmptyFuture = Futures.immediateFuture(null);

    @Nonnull
    public ListenableFuture<Bitmap> get(int pixelWidth) {
        return sEmptyFuture;
    }

    @Nonnull
    public ListenableFuture<Bitmap> getThumbnail(int pixelWidth) {
        return sEmptyFuture;
    }

    public void clearMemory() {
        // no implementation
    }

    @Keep
    static private class ArtworkImpl extends Artwork {

        @Nonnull
        final private Context mContext;

        @Nonnull
        final protected PlayerId mPlayerId;

        @Nullable
        final private String mId;

        @Nullable
        final private String mUrl;

        @GuardedBy("this")
        @Nonnull
        final private SparseArray<ListenableFuture<Bitmap>> mThumbRequests = new SparseArray<>(4);

        @GuardedBy("this")
        @Nullable
        private WeakReference<ListenableFuture<Bitmap>> mFullRequest;

        @GuardedBy("this")
        private int mFullSizeWidth;

        private enum Type {
            DISPLAY(ArtworkType.SERVER_RESOURCE_FULL, ArtworkType.ALBUM_FULL), THUMBNAIL(ArtworkType.SERVER_RESOURCE_THUMBNAIL, ArtworkType.ALBUM_THUMBNAIL);

            final ArtworkType mUrlType, mIdType;

            Type(ArtworkType urlType, ArtworkType idType) {
                mUrlType = urlType;
                mIdType = idType;
            }
        }

        /**
         * constructor for getInstance()
         */
        ArtworkImpl(Context context, PlayerId playerId, @Nullable String id, @Nullable String url) {
            mContext = context;
            mPlayerId = playerId;
            mId = id;
            mUrl = url;
        }

        @Override
        public boolean isEquivalent(@Nullable String id, @Nullable String url) {
            if (mUrl != null || url != null) {
                // if URL is non-null, use that for all equivalency checks
                return Objects.equal(mUrl, url);
            }
            return Objects.equal(mId, id);
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        @Nonnull
        public ListenableFuture<Bitmap> get(int pixelWidth) {

            // release some decoded images
            CacheServiceProvider.get().aboutToDecodeLargeArtwork();

            return getArtwork(Type.DISPLAY, pixelWidth);
        }

        @Override
        @Nonnull
        public ListenableFuture<Bitmap> getThumbnail(int pixelWidth) {
            return getArtwork(Type.THUMBNAIL, pixelWidth);
        }

        @Override
        public void clearMemory() {
            synchronized (this) {
                mFullRequest = null;
            }
        }

        @Nonnull
        protected ListenableFuture<Bitmap> getArtwork(Type localType, int pixelWidth) {
            ListenableFuture<Bitmap> retval = null;

            synchronized (this) {
                if (localType == Type.DISPLAY) {
                    if (mFullRequest != null) {
                        retval = mFullRequest.get();
                    }
                    // these are not stored in a list, only the largest retrieved bitmap is preserved
                    if (retval == null || pixelWidth > mFullSizeWidth) {
                        retval = buildFuture(Type.DISPLAY, pixelWidth);
                        mFullRequest = new WeakReference<>(retval);
                        mFullSizeWidth = pixelWidth;
                    }
                } else {
                    // localType == Type.THUMBNAIL
                    retval = mThumbRequests.get(pixelWidth);
                    if (retval == null) {
                        retval = buildFuture(Type.THUMBNAIL, pixelWidth);
                        mThumbRequests.put(pixelWidth, retval);
                    }
                }
            }

            Long potentialNewId = sFutureRequestGenerator.getAndIncrement();
            Long id = sFutureRequestMap.putIfAbsent(retval, potentialNewId);
            if (id == null) {
                id = potentialNewId;
            }

            retval = Futures.nonCancellationPropagating(retval);

            sFutureRequestMap.put(retval, id);
            return retval;
        }

        @Nonnull
        protected ListenableFuture<Bitmap> buildFuture(Type type, int width) {
            ListenableFuture<Bitmap> retval = OSExecutors.getUnboundedPool().submit(() -> loadInBackground(type, width));
            return retval;
        }

        @Nullable
        protected Bitmap loadInBackground(Type type, int pixelWidth) {
            Bitmap retval = null;
            try {
                RecyclableBitmap rBitmap = null;
                if (mUrl != null) {
                    CacheRequestCallback<ArtworkCacheData, InCacheArtworkData> request = Artwork.newCacheRequest(mContext, mUrl, type.mUrlType, pixelWidth);
                    CacheFuture<ArtworkCacheData> cacheFuture = CacheServiceProvider.get().load(request, MoreExecutors.newDirectExecutorService());
                    ArtworkCacheData data = cacheFuture.checkedGet(Constants.READ_TIMEOUT, Constants.TIME_UNITS);
                    try {
                        rBitmap = data.decodeBitmap();
                    } finally {
                        data.close();
                    }
                }
                if (rBitmap == null && mId != null) {
                    CacheRequestCallback<ArtworkCacheData, InCacheArtworkData> request = Artwork.newCacheRequest(mContext, mId, type.mIdType, pixelWidth);
                    CacheFuture<ArtworkCacheData> cacheFuture = CacheServiceProvider.get().load(request, MoreExecutors.newDirectExecutorService());
                    ArtworkCacheData data = cacheFuture.checkedGet(Constants.READ_TIMEOUT, Constants.TIME_UNITS);
                    try {
                        rBitmap = data.decodeBitmap();
                    } finally {
                        data.close();

                    }
                }
                if (rBitmap != null) {
                    // don't worry about recycling these. they will be freed using the normal Java GC eventually
                    retval = rBitmap.get();
                }
            } catch (CachedItemInvalidException | CachedItemNotFoundException e) {
                // ignore
            } catch (SBCacheException | InterruptedException | TimeoutException | IOException e) {
                OSLog.w("Error retrieving remote artwork", e);
            }
            return retval;
        }

        /**
         * use identity equals
         */
        @Override
        public boolean equals(Object o) {
            return super.equals(o);
        }

        /**
         * use identity hashcode
         */
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        @Nonnull
        public String toString() {
            return MoreObjects.toStringHelper(this).add("playerId", mPlayerId).add("id", mId).add("url", mUrl).toString();
        }
    }
}