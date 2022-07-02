/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.content.Context;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.cache.CacheEntry;
import com.orangebikelabs.orangesqueeze.cache.CacheFuture;
import com.orangebikelabs.orangesqueeze.cache.CacheFutureFactory;
import com.orangebikelabs.orangesqueeze.cache.CacheRequestCallback;
import com.orangebikelabs.orangesqueeze.cache.CacheService;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.cache.SBCacheException;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.net.PlayerStatusSubscription;
import com.orangebikelabs.orangesqueeze.net.StreamingConnection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Implementation of most common type of request. Uses Comet protocol to send response and asynchronously receive a response.
 * <p/>
 * This implementation will typically be executed in the background and for the duration of the request there is a thread waiting on the
 * response.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class CometRequest extends AbsRequest {

    @Nonnull
    final private ContextImpl mSbContext;

    @Nonnull
    final private Context mContext;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @GuardedBy("this")
    @Nullable
    private Optional<CacheEntry> mCacheEntry;

    CometRequest(Context context, ContextImpl masterContext, List<?> commands) {
        super(commands);

        mContext = context;
        mSbContext = masterContext;
    }

    @Override
    @Nonnull
    public SBResult call() throws SBRequestException, InterruptedException {
        if (!mSbContext.awaitConnection("CometRequest::call() " + getCommands())) {
            throw new SBRequestException(mContext.getString(R.string.exception_connection_timeout));
        }

        // initialize caching information
        CacheEntry cacheEntry = getCacheEntry();
        CacheFuture<? extends JsonNode> cacheFuture;
        if (cacheEntry != null) {
            // use cache...
            CacheService cacheService = CacheServiceProvider.get();
            if (shouldRefreshCache()) {
                cacheService.remove(cacheEntry);
            }
            cacheFuture = cacheService.load(mCacheCallback, MoreExecutors.newDirectExecutorService());
        } else {
            // get request without using cache
            cacheFuture = submitRequest();
        }

        try {
            JsonNode retrievedJson = cacheFuture.checkedGet(DEFAULT_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT_UNITS);
            JsonNode data = retrievedJson.get("data");
            if (data == null) {
                throw new InvalidDataException("No data element", retrievedJson);
            }

            SimpleResult retval = new SimpleResult(data);

            PlayerId playerId = getPlayerId();
            if (getCommitType() == CommitType.PLAYERUPDATE && playerId != null) {
                // when playerid gets update, commit this result
                PlayerStatusSubscription.registerCommittableResult(playerId, retval);
            } else if (getCommitType() == CommitType.IMMEDIATE) {
                retval.commit();
            }
            // finally, return result
            return retval;
        } catch (SBCacheException | TimeoutException e) {
            throw SBRequestException.wrap(e);
        }
    }

    @Nullable
    synchronized protected CacheEntry getCacheEntry() {
        Optional<CacheEntry> retval = mCacheEntry;
        if (retval == null) {
            CacheEntry newVal = null;
            if (isCacheable()) {
                CacheEntry.Type cacheType = checkServerCacheWhitelist(mCommands).orNull();
                if (cacheType != null) {
                    String cacheKey = getCacheKey(cacheType);
                    if (cacheKey != null) {
                        newVal = new CacheEntry(cacheType, mSbContext.getServerId(), cacheKey);
                    }
                }
            }
            retval = Optional.fromNullable(newVal);
            mCacheEntry = retval;
        }
        return retval.orNull();
    }

    @Nullable
    private String getCacheKey(CacheEntry.Type type) {
        if (type == CacheEntry.Type.SERVERSCAN && mSbContext.getServerStatus().getLastScanTime() == null) {
            // don't cache serverscan entries if we're scanning
            return null;
        }

        StringBuilder builder = new StringBuilder(128);
        builder.append("Request{");

        PlayerId id = getPlayerId();
        if (type == CacheEntry.Type.TIMEOUT && id != null) {
            builder.append(id);
            builder.append(",");
        }
        builder.append("[");
        boolean first = true;
        for (Object o : getCommands()) {
            if (!first) {
                builder.append(",");
            }
            builder.append(o);
            first = false;
        }
        builder.append("]}");
        return builder.toString();
    }

    @Nonnull
    protected CacheFuture<JsonNode> submitRequest() {
        try {
            // connection should already be available, if it's not then something went wrong
            // wait for connection to be available
            StreamingConnection connection = mSbContext.internalAwaitConnection("CometRequest::submitRequest() " + getCommands(), DEFAULT_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT_UNITS);
            return CacheFutureFactory.create(connection.submitRequest(DEFAULT_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT_UNITS, getPlayerId(), mCommands));
        } catch (TimeoutException | InterruptedException e) {
            return CacheFutureFactory.immediateFailedFuture(e);
        }
    }

    final private CacheRequestCallback<JsonNode, byte[]> mCacheCallback = new CacheRequestCallback<JsonNode, byte[]>() {

        @Override
        @Nonnull
        public CacheEntry getEntry() {
            //noinspection ConstantConditions
            return getCacheEntry();
        }

        /** converts ByteSource from cache to our cache result */
        @Override
        @Nonnull
        public JsonNode onDeserializeCacheData(CacheService service, ByteSource byteSource, long expectedLength) throws IOException {
            JsonParser parser = JsonHelper.createParserForData(byteSource);
            try {
                JsonNode retval = parser.readValueAsTree();
                return retval;
            } finally {
                parser.close();
            }
        }

        @Override
        @Nonnull
        public JsonNode onLoadData(CacheService service) throws InterruptedException, SBCacheException {
            try {
                return submitRequest().checkedGet(DEFAULT_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT_UNITS);
            } catch (TimeoutException e) {
                throw SBCacheException.wrap(e);
            }
        }

        @Override
        @Nullable
        public ByteSource onSerializeForDatabaseCache(CacheService service, JsonNode node, AtomicLong estimatedSize) throws IOException {
            ByteSource retval;
            if (!JsonHelper.isResponseCacheable(node)) {
                // if we're in mid-scan, don't cache this
                retval = null;
            } else {
                byte[] bytes = JsonHelper.getSmileObjectWriter().writeValueAsBytes(node);
                estimatedSize.set(bytes.length);
                retval = ByteSource.wrap(bytes);
            }
            return retval;
        }

        @Override
        public int onEstimateMemorySize(CacheService service, byte[] data) {
            return data.length;
        }

        @Nullable
        @Override
        public byte[] onAdaptForMemoryCache(CacheService service, JsonNode node) throws IOException {
            if (!JsonHelper.isResponseCacheable(node)) {
                return null;
            } else {
                AtomicInteger size = new AtomicInteger();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                JsonHelper.compactSerializeNode(node, baos, size);

                return baos.toByteArray();
            }
        }

        @Nonnull
        @Override
        public JsonNode onAdaptFromMemoryCache(CacheService service, byte[] data) throws IOException {
            AtomicInteger size = new AtomicInteger();
            return JsonHelper.deserializeNode(ByteSource.wrap(data), size);
        }

        @Override
        public boolean shouldMarkFailedRequests() {
            return false;
        }
    };

    // list of items that can be cached until the server rescans
    static final private ImmutableSet<String> sServerCacheList = ImmutableSet.of("browselibrary;items;", "artists;", "titles;", "albums;", "songinfo;");

    // list of items that can be cached with a timeout (a day or so)
    static final private ImmutableSet<String> sTimeoutCacheList = ImmutableSet.of("radios;", "myapps;items;", "picks;items;", "music;items;", "local;items;");

    // list of items that are known to be non-cacheable. This is just used to suppress warnings when unexpected commands are encountered
    static final private ImmutableSet<String> sNoCacheList = ImmutableSet.of("status;", "contextmenu;", "custombrowse;browsejive;", "menu;", "alarmsettings;",
            "jiveupdatealarm;", "favorites;items;");

    @Nonnull
    static private Optional<CacheEntry.Type> checkServerCacheWhitelist(List<Object> commands) {
        CacheEntry.Type retval = null;
        if (OSLog.isLoggable(Tag.CACHE, OSLog.VERBOSE)) {
            OSLog.v(Tag.CACHE, "cache whitelist check " + commands);
        }
        String commandString = Joiner.on(";").join(commands);
        for (String s : sServerCacheList) {
            if (commandString.startsWith(s)) {
                retval = CacheEntry.Type.SERVERSCAN;
                break;
            }
        }
        if (retval == null) {
            if (commandString.startsWith("custombrowse;browsejive;") && !commandString.contains("ml_rated")) {
                // special case for custombrowse and no custom ratings
                retval = CacheEntry.Type.SERVERSCAN;
            }
        }

        if (retval == null) {
            for (String s : sTimeoutCacheList) {
                if (commandString.startsWith(s)) {
                    retval = CacheEntry.Type.TIMEOUT;
                    break;
                }
            }
        }

        if (retval == null && OSLog.isLoggable(Tag.CACHE, OSLog.DEBUG)) {
            // check for this command on the no cache list
            boolean found = false;
            for (String s : sNoCacheList) {
                if (commandString.startsWith(s)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                OSLog.d(Tag.CACHE, "Unhandled uncached/nocachelist command string: " + commands);
            }
        }
        return Optional.fromNullable(retval);
    }

    static class InvalidDataException extends SBRequestException {
        @Nonnull
        final private JsonNode mNode;

        public InvalidDataException(String message, JsonNode node) {
            super(message);

            mNode = node;
        }

        @Nonnull
        public JsonNode getJsonNode() {
            return mNode;
        }
    }
}