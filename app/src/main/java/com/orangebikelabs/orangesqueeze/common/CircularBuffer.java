/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.collect.Lists;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class CircularBuffer<T> {
    @Nonnull
    static public <T> CircularBuffer<T> createWithFixedSize(int size) {
        return new CircularBuffer<>(size);
    }

    @Nonnull
    final private Object[] mArray;
    final private int mSize;
    private int mIndex;

    private CircularBuffer(int size) {
        mSize = size;
        mIndex = 0;
        mArray = new Object[size];
    }

    synchronized public void add(@Nullable T val) {
        mArray[mIndex] = val;

        mIndex++;

        if (mIndex >= mSize) {
            mIndex = 0;
        }
    }

    synchronized public void clear() {
        mIndex = 0;
        for (int i = 0; i < mSize; i++) {
            mArray[i] = null;
        }
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    synchronized public List<T> newSnapshot() {
        List<T> retval = Lists.newArrayListWithExpectedSize(mSize);
        for (int i = mIndex; i < mSize; i++) {
            if (mArray[i] != null) {
                retval.add((T) mArray[i]);
            }
        }
        for (int i = 0; i < mIndex; i++) {
            if (mArray[i] != null) {
                retval.add((T) mArray[i]);
            }
        }
        return retval;
    }
}
