/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.base.MoreObjects;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tsandee
 */

@ThreadSafe
public class RefCount {
    @Nonnull
    final private String mName;

    @Nonnull
    final private AtomicInteger mCount;

    @Nonnull
    static public RefCount newInstance(String name, int initialCount) {
        return new RefCount(name, initialCount);
    }

    protected RefCount(String name, int initialCount) {
        mName = name;
        mCount = new AtomicInteger(initialCount);
    }

    /**
     * return true if transitioned from off to on
     */
    public boolean increment() {
        return mCount.getAndIncrement() == 0;
    }

    /**
     * return true if transitioned from on to off
     */
    public boolean decrement() {
        return mCount.decrementAndGet() == 0;
    }

    public int count() {
        return mCount.get();
    }

    /**
     * return true if there are no references held
     */
    public boolean isFree() {
        return mCount.get() == 0;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", mName).add("count", mCount.get()).toString();
    }
}
