/*
 * Copyright (c) 2014 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.google.common.base.Stopwatch;
import com.orangebikelabs.orangesqueeze.cache.CacheEntry.Type;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class CacheEntryTest {

    private CacheEntry mKeyA1, mKeyA2;
    private CacheEntry mKeyB1;
    private ExecutorService mExecutorService;
    private Stopwatch mStopwatch = new Stopwatch();

    @Before
    public void beforeTest() {
        // these two cache entries should refer to the same lock internally
        mKeyA1 = new CacheEntry(Type.SERVERSCAN, 1, "keyA");
        mKeyA2 = new CacheEntry(Type.SERVERSCAN, 1, "keyA");
        mKeyB1 = new CacheEntry(Type.SERVERSCAN, 1, "keyB");

        mExecutorService = Executors.newCachedThreadPool();
        mStopwatch.reset();
    }

    @After
    public void afterTest() {
        mExecutorService.shutdown();
    }

    @Test
    public void testCacheEntryEquality() {
        assertThat(mKeyA1, is(mKeyA2));
    }

    @Test
    public void testCacheEntryInequality() {
        assertThat(mKeyA1, not(mKeyB1));
        assertThat(mKeyA1, not(new CacheEntry(Type.TIMEOUT, mKeyA1.getServerId(), mKeyA1.getKey())));
        assertThat(mKeyA1, not(new CacheEntry(mKeyA1.getCacheType(), mKeyA1.getServerId() + 1, mKeyA1.getKey())));
        assertThat(mKeyA1, not(new CacheEntry(mKeyA1.getCacheType(), mKeyA1.getServerId(), mKeyA1.getKey() + "added")));
    }

    @Test
    public void testCacheEntryLockFail() throws Exception {
        assertThat(mKeyA1.tryLock(0, TimeUnit.SECONDS), is(true));
        try {
            assertThat(mKeyA2.tryLock(0, TimeUnit.SECONDS), is(false));
        } finally {
            mKeyA1.releaseLock();
        }
    }

    @Test
    public void testCacheEntryLockDelayedSuccess() throws Exception {
        HoldLockCallable callable = new HoldLockCallable(mKeyA1, 5, TimeUnit.SECONDS);
        Future<Void> future = mExecutorService.submit(callable);

        assertThat(callable.mLocked.await(1, TimeUnit.SECONDS), is(true));

        mStopwatch.start();
        // now wait at least one second
        Thread.sleep(1000);
        assertThat(mKeyA2.tryLock(5, TimeUnit.SECONDS), is(true));
        try {
            assertThat(mStopwatch.elapsed(TimeUnit.MILLISECONDS) > 4000, is(true));
        } finally {
            mKeyA2.releaseLock();
        }

        // future should complete relatively quickly, one second should be plenty
        assertThat(future.get(1, TimeUnit.SECONDS), nullValue());
    }

    static class HoldLockCallable implements Callable<Void> {
        final long mTime;
        final TimeUnit mTimeUnits;
        final CacheEntry mEntry;
        final CountDownLatch mLocked = new CountDownLatch(1);

        HoldLockCallable(CacheEntry entry, long time, TimeUnit units) {
            mEntry = entry;
            mTime = time;
            mTimeUnits = units;
        }

        @Override
        public Void call() throws Exception {
            if (!mEntry.tryLock(0, TimeUnit.SECONDS)) {
                throw new IllegalStateException("expected lock to not be held");
            }
            mLocked.countDown();
            try {
                mTimeUnits.sleep(mTime);
                return null;
            } finally {
                mEntry.releaseLock();
            }
        }

    }
}
