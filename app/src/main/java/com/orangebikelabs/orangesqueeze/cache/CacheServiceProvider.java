/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.google.common.util.concurrent.Atomics;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
public class CacheServiceProvider {
    final private static AtomicReference<CacheService> sInstance = Atomics.newReference();

    @Nonnull
    static public CacheService get() {
        CacheService retval = sInstance.get();
        if (retval == null) {
            if (sInstance.compareAndSet(null, new CacheService(SBContextProvider.get().getApplicationContext()))) {
                // do nothing
            }
            retval = sInstance.get();
        }
        return retval;
    }

    private CacheServiceProvider() {
        // no constructor
    }
}
