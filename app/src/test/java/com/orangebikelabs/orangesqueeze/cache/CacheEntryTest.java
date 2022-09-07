/*
 * Copyright (c) 2014-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.google.common.base.Stopwatch;
import com.orangebikelabs.orangesqueeze.cache.CacheEntry.Type;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

public class CacheEntryTest {

    private CacheEntry mKeyA1, mKeyA2;
    private CacheEntry mKeyB1;
    private ExecutorService mExecutorService;
    final private Stopwatch mStopwatch = Stopwatch.createStarted();
    private int testIncrement = 0;

    @Before
    public void beforeTest() {
        testIncrement++;

        // key entries should not be shared between tests because their lock state might bleed over

        // these two cache entries should refer to the same lock internally
        mKeyA1 = new CacheEntry(Type.SERVERSCAN, 1, "keyA" + testIncrement);
        mKeyA2 = new CacheEntry(Type.SERVERSCAN, 1, "keyA" + testIncrement);

        // this is a different entry
        mKeyB1 = new CacheEntry(Type.SERVERSCAN, 1, "keyB" + testIncrement);

        mExecutorService = Executors.newCachedThreadPool();
        mStopwatch.reset();
    }

    @After
    public void afterTest() {
        mExecutorService.shutdown();
    }

    @Test
    public void testCacheEntryEquality() {
        assertThat(mKeyA1).isEqualTo(mKeyA2);
    }

    @Test
    public void testCacheEntryInequality() {
        assertThat(mKeyA1).isNotEqualTo(mKeyB1);
        assertThat(mKeyA1).isNotEqualTo(new CacheEntry(Type.TIMEOUT, mKeyA1.getServerId(), mKeyA1.getKey()));
        assertThat(mKeyA1).isNotEqualTo(new CacheEntry(mKeyA1.getCacheType(), mKeyA1.getServerId() + 1, mKeyA1.getKey()));
        assertThat(mKeyA1).isNotEqualTo(new CacheEntry(mKeyA1.getCacheType(), mKeyA1.getServerId(), mKeyA1.getKey() + "added"));
    }

    /**
     * test the reentrant capabilites of the locking. Same key entry value but different key objects, same thread
     */
    @Test
    public void testCacheEntryLockSuccess() throws Exception {
        assertThat(mKeyA1.tryLock(0, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(mKeyA2.tryLock(0, TimeUnit.SECONDS)).isTrue();
            mKeyA2.releaseLock();
        } finally {
            mKeyA1.releaseLock();
        }
    }

    /**
     * test the reentrant capabilites of the locking. Same key entry value but different key objects, different thread
     */
    @Test
    public void testCacheEntryLockFailure() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(1);
        Future<Void> future = mExecutorService.submit(() -> {
            assertThat(mKeyA1.tryLock(0, TimeUnit.SECONDS)).isTrue();
            latch.countDown();
            assertThat(latch2.await(1, TimeUnit.SECONDS)).isTrue();
            mKeyA1.releaseLock();
            return null;
        });
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(mKeyA2.tryLock(0, TimeUnit.SECONDS)).isFalse();
        latch2.countDown();

        assertThat(future.get(1, TimeUnit.SECONDS)).isNull();
    }

    /**
     * tests that the tryLock method will wait some time if there is a lengthy operation holding the lock
     */
    @Test
    public void testCacheEntryLockDelayedSuccess() throws Exception {
        final int WAIT_SECONDS = 5;
        final CountDownLatch lockHeldLatch = new CountDownLatch(1);
        Future<Void> future = mExecutorService.submit(() -> {
            assertThat(mKeyA1.tryLock(0, TimeUnit.SECONDS)).isTrue();
            try {
                // signal that lock is held
                lockHeldLatch.countDown();
                // hold the lock for 5 seconds
                TimeUnit.SECONDS.sleep(WAIT_SECONDS);
                return null;
            } finally {
                mKeyA1.releaseLock();
            }
        });

        // wait for lock to be held
        assertWithMessage("lock is held").that(lockHeldLatch.await(1, TimeUnit.SECONDS)).isTrue();

        mStopwatch.start();
        // now wait at least one second
        Thread.sleep(1000);
        assertWithMessage("lock acquired").that(mKeyA2.tryLock(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        try {
            assertWithMessage("we actually waited 4 seconds").that(mStopwatch.elapsed(TimeUnit.MILLISECONDS)).isGreaterThan((WAIT_SECONDS - 1) * 1000L);
        } finally {
            mKeyA2.releaseLock();
        }

        // future should complete relatively quickly, one second should be plenty
        assertThat(future.get(1, TimeUnit.SECONDS)).isNull();
    }
}
