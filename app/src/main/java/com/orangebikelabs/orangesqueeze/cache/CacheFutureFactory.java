/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.orangebikelabs.orangesqueeze.common.OSLog;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;

/**
 * Cache Future Factory methods.
 */
public class CacheFutureFactory {
    private CacheFutureFactory() {
    }

    @Nonnull
    static public <T> CacheFuture<T> create(ListenableFuture<T> future) {
        return new SimpleCacheFuture<>(future);
    }

    @Nonnull
    static public <T> CacheFuture<T> immediateFuture(T value) {
        return new SimpleCacheFuture<>(Futures.immediateFuture(value));
    }

    @Nonnull
    static public <T> CacheFuture<T> immediateFailedFuture(Throwable t) {
        return new SimpleCacheFuture<>(Futures.immediateFailedFuture(t));
    }

    static class SimpleCacheFuture<T> extends ForwardingListenableFuture.SimpleForwardingListenableFuture<T> implements CacheFuture<T> {
        protected SimpleCacheFuture(ListenableFuture<T> delegate) {
            super(delegate);
        }

        @Override
        @Nonnull
        public T checkedGet(long time, TimeUnit units) throws SBCacheException, InterruptedException, TimeoutException {
            try {
                T retval = get(time, units);
                if (retval == null) {
                    throw new IllegalStateException("CacheFuture.get() should never return null");
                }
                return retval;
            } catch (ExecutionException e) {
                OSLog.d(OSLog.Tag.CACHE, "Cache request failed with exception", e);

                Throwable cause = e.getCause();
                Throwables.propagateIfPossible(cause, InterruptedException.class);
                Throwables.propagateIfPossible(cause, TimeoutException.class);
                Throwables.propagateIfPossible(cause, SBCacheException.class);
                throw SBCacheException.wrap(cause);
            }
        }
    }
}
