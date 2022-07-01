/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.google.common.io.ByteSource;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Callback interface for requests that are marked cacheable.
 * <p/>
 *
 * @author tsandee
 */
public interface CacheRequestCallback<T, C> {
    /**
     * Adapt stored cached data in bytestream format to the deserialized format. Normally this is some variety of deserialization
     */
    @Nonnull
    T onDeserializeCacheData(CacheService service, ByteSource byteSource, long expectedLength) throws IOException;

    /**
     * Go out and get the cached item from wherever it comes from.
     */
    @Nonnull
    T onLoadData(CacheService service) throws InterruptedException, IOException, SBCacheException;

    /**
     * Adapt the supplied data to a ByteSource and an estimated output size.
     */
    @Nullable
    ByteSource onSerializeForDatabaseCache(CacheService service, T data, AtomicLong outEstimatedSize) throws IOException;

    /**
     * Estimate the size (in memory) of the supplied object
     */
    int onEstimateMemorySize(CacheService service, C dataToEstimate);

    /**
     * Adapt for memory cache. May return the same object, or null, or a new value suitable for the memory cache.
     */
    @Nullable
    C onAdaptForMemoryCache(CacheService service, T dataToAdapt) throws IOException;

    /**
     * Adapt from memory cache for client consumption. Must return an object; can return the same.
     */
    @Nonnull
    T onAdaptFromMemoryCache(CacheService service, C dataToAdapt) throws IOException;

    /**
     * Retrieve the cache entry for the specified request
     */
    @Nonnull
    CacheEntry getEntry();

    /**
     * Do we mark failed requests and remember them as such? Typically only artwork.
     */
    boolean shouldMarkFailedRequests();
}
