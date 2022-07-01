/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.cache.CacheFuture;
import com.orangebikelabs.orangesqueeze.cache.CacheService;
import com.orangebikelabs.orangesqueeze.cache.SBCacheException;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.common.SimpleLoopingRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
public class LegacyAlbumThumbnailCacheRequest extends StandardArtworkCacheRequest {
    final private String mAlbumId;

    public LegacyAlbumThumbnailCacheRequest(Context context, String albumId, int pixelWidth) {
        super(context, "lart:" + albumId, ArtworkType.LEGACY_ALBUM_THUMBNAIL, pixelWidth);
        mAlbumId = albumId;
    }

    @Override
    @Nonnull
    public ArtworkCacheData onLoadData(CacheService service) throws SBCacheException, IOException, InterruptedException {
        Preconditions.checkArgument(mType == ArtworkType.LEGACY_ALBUM_THUMBNAIL, "artwork type must be LEGACY_ALBUM_THUMBNAIL");

        // do data load on the current thread because artwork requests are throttled elsewhere

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
        request.addParameter("album_id", mAlbumId);

        request.setLoopAndCountKeys(Collections.singletonList("titles_loop"), null);
        request.submit(MoreExecutors.newDirectExecutorService());
        if (request.isAborted()) {
            throw new InterruptedException();
        }

        String trackId = null;
        for (ObjectNode node : items) {
            JsonNode tid = node.get("id");
            if (tid != null) {
                trackId = tid.asText();
                break;
            }
        }

        if (trackId == null) {
            throw new SBCacheException("no track data");
        }
        try {
            ArtworkCacheRequestCallback delegating = Artwork.newCacheRequest(mContext, trackId, ArtworkType.ALBUM_THUMBNAIL, mWidthPixels);
            CacheFuture<ArtworkCacheData> future = service.load(delegating, MoreExecutors.newDirectExecutorService());
            return future.get();
        } catch(ExecutionException e) {
            throw SBCacheException.wrap(e);
        }
    }
}
