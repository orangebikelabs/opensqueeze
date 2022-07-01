/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.util.concurrent.AbstractFuture;

import java.util.concurrent.TimeUnit;

/**
 * A ListenableFuture that makes a value available after the specified duration has elapsed.
 *
 * @author tsandee
 */
public class DelayedFuture<V> extends AbstractFuture<V> {
    public DelayedFuture(final V value, long time, TimeUnit unit) {
        OSExecutors.getSingleThreadScheduledExecutor().schedule((Runnable) () -> set(value), time, unit);
    }
}
