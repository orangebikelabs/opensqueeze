/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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
