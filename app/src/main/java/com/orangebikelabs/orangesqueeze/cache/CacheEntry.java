/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.hash.Hashing;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.RefCount;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable cache entry object that also provides simple locking to prevent multiple requests on the same data.
 *
 * @author tsandee
 */
@Keep
@Immutable
public class CacheEntry {

    final static private Object sLockGuard = new Object();

    @GuardedBy("sLockGuard")
    final static private Map<CacheEntry, LockHolder> sLockMap = new HashMap<>();

    @GuardedBy("sLockGuard")
    final static private Queue<LockHolder> sLockPool = new ArrayBlockingQueue<>(10);

    public enum Type {
        SERVERSCAN, TIMEOUT
    }

    @Nonnull
    final private String mKey;

    final private int mKeyHash;

    @Nonnull
    final private Type mType;

    final private long mServerId;

    volatile private LockHolder mHeldLock;

    public CacheEntry(Type cacheType, long serverId, String key) {
        mType = cacheType;
        mServerId = serverId;
        mKey = key;

        // use slightly better hash function than standard for persistent hash codes
        // IF YOU CHANGE THIS YOU MUST BUMP THE DATABASE REV!
        // this is plenty good for general hashing as well, don't bother creating a better hash function
        mKeyHash = Hashing.murmur3_32_fixed().hashUnencodedChars(key).asInt();
    }

    @Nonnull
    public String getKey() {
        return mKey;
    }

    public int getKeyHash() {
        return mKeyHash;
    }

    @Nonnull
    public Type getCacheType() {
        return mType;
    }

    public long getServerId() {
        return mServerId;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("type", mType).
                add("keyHash", mKeyHash).
                add("serverId", mServerId).
                add("key", mKey).
                toString();
    }

    @Override
    public int hashCode() {
        return mKeyHash;
    }

    /**
     * Attempt to acquire a lock on the specified cache entry. This is unique to the entry based on object equality, not identity.
     */
    public boolean tryLockNonBlocking() {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        boolean acquired = false;
        LockHolder lock = LockHolder.acquire(this);
        try {
            acquired = lock.mLock.tryLock(0, TimeUnit.SECONDS);
            if (acquired) {
                mHeldLock = lock;
            }
        } catch (InterruptedException e) {
            // in general, this should be very rare, if not impossible
        } finally {
            if (!acquired) {
                lock.release(this);
            }
        }
        return acquired;
    }

    /**
     * Acquire a lock on the specified cache entry. This is unique to the entry based on object equality, not identity.
     */
    public boolean tryLock(long timeout, TimeUnit units) throws InterruptedException {
        LockHolder lock = LockHolder.acquire(this);

        boolean acquired = lock.mLock.tryLock(timeout, units);
        if (acquired) {
            mHeldLock = lock;
        } else {
            // release back to the pool
            lock.release(this);
        }

        return acquired;
    }

    /**
     * Release a held lock on the cache entry.
     */
    public void releaseLock() {
        LockHolder lock = mHeldLock;
        mHeldLock = null;

        OSAssert.assertNotNull(lock, "releasing unheld lock on entry");

        // release the actual lock
        lock.mLock.unlock();

        // release it back to the pool or decrement the refcount
        lock.release(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        CacheEntry other = (CacheEntry) obj;
        if (!Objects.equal(mKey, other.mKey)) {
            return false;
        }
        if (mServerId != other.mServerId) {
            return false;
        }
        if (mType != other.mType) {
            return false;
        }
        return true;
    }

    static private class LockHolder extends RefCount {
        final public ReentrantLock mLock = new ReentrantLock();

        @Nonnull
        static LockHolder acquire(CacheEntry entry) {
            synchronized (sLockGuard) {
                LockHolder retval = sLockMap.get(entry);
                if (retval == null) {
                    retval = sLockPool.poll();

                    if (retval == null) {
                        retval = new LockHolder();
                    }
                    sLockMap.put(entry, retval);
                }
                retval.increment();
                return retval;
            }
        }

        LockHolder() {
            super("lock", 0);
        }

        public void release(CacheEntry entry) {
            synchronized (sLockGuard) {
                if (decrement()) {
                    // remove from map
                    sLockMap.remove(entry);

                    // add to pool of available locks to reuse
                    sLockPool.offer(this);
                }
            }
        }
    }
}
