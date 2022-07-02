/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
