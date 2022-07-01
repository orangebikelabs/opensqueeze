/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import androidx.collection.LruCache;

import com.google.common.base.Objects;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.event.CurrentServerState;
import com.squareup.otto.Subscribe;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A small memory cache designed to work with the cache service.
 * <p/>
 * Memory cache can be bound to a servicemanager.listener object. It will register for global events using the lifecycle of the
 * servicemanager.
 *
 * @author tsandee
 */
@ThreadSafe
public class MemoryCache {
    final private LocalLruCache mCache;
    final private CacheService mService;
    final protected AtomicLong mServerScanTime = new AtomicLong(0);

    // main thread only
    private boolean mRegistered;

    public MemoryCache(CacheService service, CacheConfiguration configuration) {
        mCache = new LocalLruCache(configuration);
        mService = service;
    }

    public int memorySize() {
        return mCache.size();
    }

    public boolean remove(CacheEntry entry) {
        return mCache.remove(entry) != null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T get(CacheEntry entry) throws CachedItemNotFoundException, CachedItemInvalidException {
        Value value = mCache.get(entry);
        if (value != null) {
            // check for expired values in memory
            boolean expired = false;

            switch (entry.getCacheType()) {
                case SERVERSCAN:
                    if (value.mExpiration != mServerScanTime.get()) {
                        expired = true;
                    }
                    break;
                case TIMEOUT:
                    if (value.mExpiration < System.currentTimeMillis()) {
                        expired = true;
                    }
                    break;
                default:
                    throw new IllegalStateException("unknown cache entry type: " + entry.getCacheType());
            }
            if (expired) {
                mCache.removeIfValue(entry, value);
                value = null;
            }
        }
        if (value != null && value.mValue != null) {
            return (T) value.mValue;
        } else if (value instanceof MissingValue) {
            throw new CachedItemNotFoundException("Missing, marked as such in memory cache");
        } else if (value instanceof InvalidValue) {
            throw new CachedItemInvalidException("Invalid, marked as such in memory cache");
        } else {
            return null;
        }
    }

    public <T, C> void put(CacheEntry entry, CacheRequestCallback<T, C> request, C value) {
        long expiration = getExpiration(entry);

        if (OSLog.isLoggable(OSLog.Tag.CACHE, OSLog.VERBOSE)) {
            OSLog.v(OSLog.Tag.CACHE, "adding " + value.getClass().getName() + " to memory cache for " + entry);
        }
        int size = request.onEstimateMemorySize(mService, value);
        mCache.put(entry, new Value(value, size, expiration));
    }

    public void markMissing(CacheEntry entry) {
        long expiration = getExpiration(entry);

        mCache.put(entry, new MissingValue(expiration));
    }

    public void markInvalid(CacheEntry entry) {
        long expiration = getExpiration(entry);

        mCache.put(entry, new InvalidValue(expiration));
    }

    public void clear() {
        mCache.evictAll();
    }

    public void listenTo(ServiceManager manager) {
        manager.addListener(mServiceListener, OSExecutors.getMainThreadExecutor());
    }

    private long getExpiration(CacheEntry entry) {
        long newExpiration;
        switch (entry.getCacheType()) {
            case SERVERSCAN:
                newExpiration = mServerScanTime.get();
                break;
            case TIMEOUT:
                newExpiration = mService.getFutureCacheTimeout();
                break;
            default:
                throw new IllegalStateException("unknown cache type: " + entry.getCacheType());
        }
        return newExpiration;
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenServerStateChanges(CurrentServerState state) {
            Long lastScan = state.getServerStatus().getLastScanTime();

            if (lastScan == null) {
                // if the server is mid-scan, leave cache intact for now
                return;
            }

            if (!Objects.equal(mServerScanTime.getAndSet(lastScan), lastScan)) {
                // clear cache if the scan time has changed
                clear();
            }
        }
    };

    static private class Value {
        @Nullable
        final Object mValue;
        final int mSize;
        final long mExpiration;

        Value(@Nullable Object value, int size, long expiration) {
            mSize = size;
            mValue = value;
            mExpiration = expiration;
        }
    }

    static class MissingValue extends Value {
        MissingValue(long expiration) {
            super(null, 0, expiration);
        }
    }

    static class InvalidValue extends Value {
        InvalidValue(long expiration) {
            super(null, 0, expiration);
        }
    }

    static private class LocalLruCache extends LruCache<CacheEntry, Value> {
        public LocalLruCache(CacheConfiguration config) {
            super(config.getMaxMemorySize());
        }

        @Override
        protected int sizeOf(CacheEntry key, Value value) {
            return value.mSize;
        }

        @Override
        protected void entryRemoved(boolean evicted, CacheEntry key, @Nonnull Value oldValue, @Nullable Value newValue) {
            super.entryRemoved(evicted, key, oldValue, newValue);
            Object o = oldValue.mValue;
            
            if (o instanceof OptionalCacheValueOperations) {
                ((OptionalCacheValueOperations) o).onPurgeFromMemoryCache();
            }
        }

        synchronized public void removeIfValue(CacheEntry key, Value value) {
            Value current = get(key);
            if (Objects.equal(current, value)) {
                remove(key);
            }
        }
    }

    final private ServiceManager.Listener mServiceListener = new ServiceManager.Listener() {

        @Override
        public void failure(Service service) {
            // intentionally blank
        }

        @Override
        public void healthy() {
            if (!mRegistered) {
                mRegistered = true;
                BusProvider.getInstance().register(mEventReceiver);
            }
        }

        @Override
        public void stopped() {
            if (mRegistered) {
                mRegistered = false;
                BusProvider.getInstance().unregister(mEventReceiver);
            }

            clear();
        }
    };

}
