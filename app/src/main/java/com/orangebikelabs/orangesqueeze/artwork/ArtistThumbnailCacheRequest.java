/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.math.IntMath;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.cache.CacheFuture;
import com.orangebikelabs.orangesqueeze.cache.CacheRequestCallback;
import com.orangebikelabs.orangesqueeze.cache.CacheService;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.cache.CachedItemNotFoundException;
import com.orangebikelabs.orangesqueeze.cache.SBCacheException;
import com.orangebikelabs.orangesqueeze.common.Closeables;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.common.SimpleLoopingRequest;
import com.orangebikelabs.orangesqueeze.common.VersionIdentifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
public class ArtistThumbnailCacheRequest extends StandardArtworkCacheRequest {
    final static private long TASK_KEEPALIVE = 10000;
    final static private TimeUnit TASK_KEEPALIVE_UNITS = TimeUnit.MILLISECONDS;

    final static private ListeningExecutorService sBoundedPool;
    final static private VersionIdentifier sVersionSevenEight = new VersionIdentifier("7.8.0");
    final static private VersionIdentifier sVersionSevenSix = new VersionIdentifier("7.6.0");

    static {
        final int taskCount = 6;
        OSExecutors.SafeThreadPoolExecutor boundedTemp = new OSExecutors.SafeThreadPoolExecutor(taskCount, taskCount, TASK_KEEPALIVE, TASK_KEEPALIVE_UNITS,
                new LinkedBlockingQueue<>(), Thread.NORM_PRIORITY - 1, "Artist Artwork pool # %1$d");
        boundedTemp.allowCoreThreadTimeOut(true);
        sBoundedPool = MoreExecutors.listeningDecorator(boundedTemp);
    }

    final protected int mMaxGridSize;

    public ArtistThumbnailCacheRequest(Context context, String id, int pixelWidth) {
        super(context, id, ArtworkType.ARTIST_THUMBNAIL, pixelWidth);

        int minimumComponentSize = context.getResources().getDimensionPixelSize(R.dimen.artistartwork_component_minimum_size);

        // make sure grid size can expand as long as individual images are 60dp or higher
        mMaxGridSize = Math.max(2, pixelWidth / minimumComponentSize);
    }

    @Override
    @Nonnull
    public ArtworkCacheData onLoadData(CacheService service) throws SBCacheException, IOException, InterruptedException {
        Preconditions.checkArgument(mType == ArtworkType.ARTIST_THUMBNAIL, "artwork type must be ARTIST_THUMBNAIL");

        // do data load on the current thread because artwork requests are throttled elsewhere

        List<String> artworkIdList = new ArrayList<>();

        VersionIdentifier current = SBContextProvider.get().getServerStatus().getVersion();
        if (current.compareTo(sVersionSevenSix) >= 0) {
            final List<ObjectNode> items = new ArrayList<>();
            SimpleLoopingRequest request = new SimpleLoopingRequest(null) {
                @Override
                protected void onLoopItem(SBResult loopingResult, ObjectNode item) throws SBRequestException {
                    super.onLoopItem(loopingResult, item);
                    items.add(item);
                }
            };
            request.setCommands("albums");
            request.setCacheable(false);
            request.addParameter("artist_id", mId);
            request.addParameter("tags", "jw");
            if (current.compareTo(sVersionSevenEight) >= 0) {
                // use artflow for 7.8 and higher
                request.addParameter("sort", "artflow");
            } else if (current.compareTo(sVersionSevenSix) >= 0) {
                // user sort:new for 7.6 and higher
                request.addParameter("sort", "new");
            }

            request.setLoopAndCountKeys(Collections.singletonList("albums_loop"), null);
            request.submit(MoreExecutors.newDirectExecutorService());
            if (request.isAborted()) {
                throw new InterruptedException();
            }

            // non-compilation
            for (ObjectNode node : items) {
                if (node.path("compilation").asInt() == 0) {
                    JsonNode artworkId = node.get("artwork_track_id");
                    if (artworkId != null) {
                        artworkIdList.add(artworkId.asText());
                    }
                }
            }

            // compilation
            for (ObjectNode node : items) {
                if (node.path("compilation").asInt() != 0) {
                    JsonNode artworkId = node.get("artwork_track_id");
                    if (artworkId != null) {
                        artworkIdList.add(artworkId.asText());
                    }
                }
            }
        } else {
            final List<ObjectNode> items = new ArrayList<>();
            SimpleLoopingRequest request = new SimpleLoopingRequest(null) {
                @Override
                protected void onLoopItem(SBResult loopingResult, ObjectNode item) throws SBRequestException {
                    super.onLoopItem(loopingResult, item);
                    items.add(item);
                }
            };
            request.setCommands("titles");
            request.setCacheable(false);
            request.addParameter("artist_id", mId);
            request.addParameter("tags", "Ce");

            request.setLoopAndCountKeys(Collections.singletonList("titles_loop"), null);
            request.submit(MoreExecutors.newDirectExecutorService());
            if (request.isAborted()) {
                throw new InterruptedException();
            }

            Set<String> usedAlbumIds = new HashSet<>();

            // non-compilation
            for (ObjectNode node : items) {
                if (node.path("compilation").asInt() == 0) {
                    JsonNode albumId = node.get("album_id");
                    JsonNode trackId = node.get("id");
                    if (albumId != null) {
                        String aid = albumId.asText();
                        if (usedAlbumIds.add(aid)) {
                            artworkIdList.add(trackId.asText());
                        }
                    }
                }
            }

            // compilation
            for (ObjectNode node : items) {
                if (node.path("compilation").asInt() != 0) {
                    JsonNode albumId = node.get("album_id");
                    JsonNode trackId = node.get("id");
                    if (albumId != null) {
                        String aid = albumId.asText();
                        if (usedAlbumIds.add(aid)) {
                            artworkIdList.add(trackId.asText());
                        }
                    }
                }
            }
        }

        final List<String> fList = artworkIdList;
        ArtworkBuilder builder = new GridArtworkBuilder();
        return builder.build(Math.min(mMaxGridSize * mMaxGridSize, artworkIdList.size()), (ndx, artworkDimensions) -> {
                    return Artwork.newCacheRequest(mContext, fList.get(ndx), ArtworkType.ALBUM_THUMBNAIL, artworkDimensions);
                },
                sBoundedPool);
    }

    interface ArtworkBuilder {
        ArtworkCacheData build(int artworkCount, ArtworkSupplier supplier, ListeningExecutorService executor) throws SBCacheException, IOException,
                InterruptedException;
    }

    interface ArtworkSupplier {
        ArtworkCacheRequestCallback getArtwork(int ndx, int dimensions);
    }

    final static protected ReentrantLock sArtistArtworkSerializationLock = new ReentrantLock();

    class GridArtworkBuilder implements ArtworkBuilder {

        private int mGridSize;

        @Override
        public ArtworkCacheData build(int artworkCount, ArtworkSupplier supplier, ListeningExecutorService executor) throws SBCacheException, IOException,
                InterruptedException {
            mGridSize = mMaxGridSize;

            OSLog.v(Tag.ARTWORK, "Build artwork for id=" + mId + ", album count=" + artworkCount + ", maxGridSize=" + mMaxGridSize);
            for (int i = 1; i <= mMaxGridSize; i++) {
                if (artworkCount < IntMath.pow(i, 2)) {
                    mGridSize = i - 1;
                    break;
                }
            }

            OSLog.v(Tag.ARTWORK, "calculated grid size: " + mGridSize);

            try {
                if (mGridSize == 0) {
                    throw new CachedItemNotFoundException("no artist artwork");
                } else if (mGridSize == 1) {
                    ArtworkCacheRequestCallback request = supplier.getArtwork(0, mWidthPixels);
                    CacheFuture<ArtworkCacheData> future = CacheServiceProvider.get().load(request, MoreExecutors.newDirectExecutorService());
                    return future.get(Constants.READ_TIMEOUT, Constants.TIME_UNITS);
                } else {
                    List<CacheFuture<ArtworkCacheData>> list = new ArrayList<>();
                    for (int x = 0; x < mGridSize; x++) {
                        for (int y = 0; y < mGridSize; y++) {

                            int thumbDimension = getGridCellArtworkWidth(x, y);
                            int index = y * mGridSize + x;

                            CacheRequestCallback<ArtworkCacheData, InCacheArtworkData> request = supplier.getArtwork(index, thumbDimension);
                            list.add(CacheServiceProvider.get().load(request, executor));
                        }
                    }
                    ListenableFuture<List<ArtworkCacheData>> futureList = Futures.successfulAsList(list);

                    if (!sArtistArtworkSerializationLock.tryLock(30, TimeUnit.SECONDS)) {
                        throw new SBCacheException("timeout waiting for artwork lock");
                    }
                    boolean success = false;
                    try {
                        ArrayList<ArtworkCacheData> artworkList = new ArrayList<>(futureList.get(Constants.READ_TIMEOUT, Constants.TIME_UNITS));
                        Iterables.removeIf(artworkList, Predicates.isNull());
                        ArtworkCacheData retval = createArtistArtwork(artworkList);
                        success = true;
                        return retval;
                    } finally {
                        sArtistArtworkSerializationLock.unlock();

                        // release all the artwork data
                        for (CacheFuture<ArtworkCacheData> f : list) {
                            if (f.isDone()) {
                                f.get().close();
                            } else if (!success) {
                                Futures.addCallback(f, Closeables.getCloserCallback(), MoreExecutors.directExecutor());
                                f.cancel(true);
                            }
                        }
                    }
                }
            } catch (TimeoutException | OutOfMemoryError e) {
                throw SBCacheException.wrap(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof InterruptedException) {
                    throw (InterruptedException) e.getCause();
                }
                throw SBCacheException.wrap(e);
            }
        }

        /**
         * due to bugs on some phone implementations, this method must be run in a serialized fashion (thus the lock when calling it above
         */
        @Nonnull
        private ArtworkCacheData createArtistArtwork(List<ArtworkCacheData> artworkList) throws IOException, CachedItemNotFoundException {

            final boolean verboseLog = OSLog.isLoggable(Tag.ARTWORK, OSLog.VERBOSE);

            if (verboseLog) {
                OSLog.v(Tag.ARTWORK, "create bitmap");
            }
            // create artwork at highest quality, later we will down-sample to save memory
            @SuppressWarnings({"UnnecessaryLocalVariable", "SuspiciousNameCombination"}) final int height = mWidthPixels;

            Bitmap destinationBitmap = Bitmap.createBitmap(mWidthPixels, height, Bitmap.Config.ARGB_8888);
            try {
                destinationBitmap.setDensity(mContext.getResources().getDisplayMetrics().densityDpi);
                destinationBitmap.eraseColor(Color.TRANSPARENT);

                Paint insideBorder = new Paint();
                insideBorder.setColor(0xFFC0C0C0);
                insideBorder.setStrokeWidth(0);
                insideBorder.setStyle(Style.STROKE);

                Paint outsideBorder = new Paint();
                outsideBorder.setColor(0xFF000000);
                outsideBorder.setStrokeWidth(0);
                outsideBorder.setStyle(Style.STROKE);

                Paint blackFill = new Paint();
                blackFill.setColor(0xFF000000);
                blackFill.setStyle(Style.FILL);

                Canvas c = new Canvas();
                c.setBitmap(destinationBitmap);

                int currentCell = 0;
                for (ArtworkCacheData data : artworkList) {
                    RecyclableBitmap bmp = data.decodeBitmap();
                    if (bmp == null) {
                        continue;
                    }
                    try {
                        Rect dest = getGridCellRect(currentCell);
                        c.drawBitmap(bmp.get(), null, dest, null);
                        currentCell++;
                    } finally {
                        bmp.recycle();
                    }
                }
                if (currentCell == 0) {
                    throw new CachedItemNotFoundException("no artist artwork");
                }

                return new ExistingBitmapArtworkData(mContext, mEntry.getKey(), mType, destinationBitmap);
            } finally {
                destinationBitmap.recycle();
            }
        }

        protected Rect getGridCellRect(int cell) {
            int x = cell % mGridSize;
            int y = cell / mGridSize;
            int startX = getGridCellX(x, y);
            int startY = getGridCellY(x, y);

            int width = getGridCellWidth(x, y);
            int height = getGridCellHeight(x, y);
            return new Rect(startX, startY, startX + width, startY + height);
        }

        private int getGridCellArtworkWidth(int x, int y) {
            return Math.max(getGridCellWidth(x, y), getGridCellHeight(x, y));
        }

        private int getGridCellWidth(int x, int y) {
            int startX = getGridCellX(x, y);
            int endX = (x + 1) * mWidthPixels / mGridSize;

            return endX - startX;
        }

        private int getGridCellHeight(int x, int y) {
            int startY = getGridCellY(x, y);
            int endY = (y + 1) * mWidthPixels / mGridSize;

            return endY - startY;
        }

        private int getGridCellX(int x, int y) {
            return x * mWidthPixels / mGridSize;
        }

        private int getGridCellY(int x, int y) {
            return y * mWidthPixels / mGridSize;
        }
    }
}
