/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
public interface CacheFuture<T> extends ListenableFuture<T> {
    @Nonnull
    T checkedGet(long time, TimeUnit units) throws SBCacheException, InterruptedException, TimeoutException;
}
