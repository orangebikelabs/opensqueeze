/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Simple collection that holds references to a fixed number of objects.
 *
 * @author tsandee
 */
public class ReferenceBag<T> {
    @Nonnull
    static public <T> ReferenceBag<T> createWithFixedSize(int size) {
        return new ReferenceBag<>(size);
    }

    final private AtomicReferenceArray<T> mArray;
    final private AtomicInteger mIndex = new AtomicInteger();
    final private int mSize;

    private ReferenceBag(int size) {
        mSize = size;
        mArray = new AtomicReferenceArray<>(size);
    }

    /**
     * return any expunged entry
     */
    @Nullable
    public T add(T val) {
        int ndx = mIndex.getAndIncrement() % mSize;
        return mArray.getAndSet(ndx, val);
    }

    /**
     * return list of expunged entries
     */
    @Nonnull
    public List<T> clear() {
        List<T> retval = new ArrayList<>();

        mIndex.set(0);
        for (int i = 0; i < mSize; i++) {
            T oldVal = mArray.getAndSet(i, null);
            if (oldVal != null) {
                retval.add(oldVal);
            }
        }
        return retval;
    }
}
