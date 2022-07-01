/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.base.Objects;
import com.google.common.util.concurrent.Monitor;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides simple latch capabilities with arbitrary values.
 *
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
public class EqualityLatch<T> {
    /**
     * type-inferring static create method
     */
    @Nonnull
    static public <T> EqualityLatch<T> create(@Nullable T initialValue) {
        return new EqualityLatch<>(initialValue);
    }

    final protected Monitor mMonitor = new Monitor();

    @GuardedBy("mMonitor")
    @Nullable
    private String mTraceName;

    @GuardedBy("mMonitor")
    @Nullable
    protected T mValue;

    private EqualityLatch(@Nullable T initialValue) {
        mValue = initialValue;
    }

    public void setTraceName(@Nullable String traceName) {
        mMonitor.enter();
        try {
            mTraceName = traceName;
        } finally {
            mMonitor.leave();
        }
    }

    public boolean await(@Nullable T value, long timeout, TimeUnit units) throws InterruptedException {
        boolean retval = mMonitor.enterWhen(new EqualityGuard(value), timeout, units);
        if (retval) {
            // do nothing, leave immediately
            mMonitor.leave();
        }
        return retval;
    }

    public void await(@Nullable T value) throws InterruptedException {
        mMonitor.enterWhen(new EqualityGuard(value));
        // do nothing, leave immediately
        mMonitor.leave();
    }

    @Nullable
    public T get() {
        mMonitor.enter();
        try {
            return mValue;
        } finally {
            mMonitor.leave();
        }
    }

    public void set(@Nullable T value) {
        mMonitor.enter();
        try {
            if (mValue != value) {
                if (mTraceName != null) {
                    OSLog.v("Changing equality latch " + mTraceName + " to " + value);
                }
                mValue = value;
            }
        } finally {
            mMonitor.leave();
        }
    }

    private class EqualityGuard extends Monitor.Guard {
        @Nullable
        final private T mGuardValue;

        EqualityGuard(@Nullable T value) {
            super(mMonitor);

            mGuardValue = value;
        }

        @Override
        public boolean isSatisfied() {
            return Objects.equal(mValue, mGuardValue);
        }
    }
}
