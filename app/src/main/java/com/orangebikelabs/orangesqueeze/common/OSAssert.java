/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Objects;
import com.google.common.util.concurrent.Monitor;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Assertions added to the debug builds of the app. These are stripped outby proguard for release builds.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class OSAssert {
    static public void assertNotMainThread() {
        if (ThreadTools.isMainThread()) {
            throw new IllegalStateException("cannot call from main thread in this situation");
        }
    }

    static public void assertNotReach() {
        throw new IllegalStateException("should never reach this");
    }

    static public void assertApplicationContext(Context context) {
        if ((context instanceof Activity) || (context instanceof Service)) {
            throw new IllegalStateException("context must not be activity or service context");
        }
        if (context instanceof ContextWrapper) {
            ContextWrapper wrapper = (ContextWrapper) context;

            assertApplicationContext(wrapper.getBaseContext());
        }

    }

    static public void assertMainThread() {
        if (!ThreadTools.isMainThread()) {
            throw new IllegalStateException("must call from main thread in this situation");
        }
    }

    static public void assertCurrentThreadHoldsMonitor(Monitor monitor) {
        if (!monitor.isOccupiedByCurrentThread()) {
            throw new IllegalStateException("must hold specified monitor when calling method");
        }
    }

    static public void assertCurrentThreadHoldsWriteLock(ReentrantReadWriteLock lock) {
        if (!lock.isWriteLockedByCurrentThread()) {
            throw new IllegalStateException("must hold write lock when calling method");
        }
    }

    static public void assertCurrentThreadHoldsReadLock(ReentrantReadWriteLock lock) {
        if (lock.getReadHoldCount() == 0) {
            throw new IllegalStateException("must hold read lock when calling method");
        }
    }

    public static void assertTrue(boolean value, String msg) {
        if (!value) {
            throw new IllegalStateException(msg);
        }
    }

    public static void assertFalse(boolean value, String msg) {
        if (value) {
            throw new IllegalStateException(msg);
        }
    }

    public static void assertEquals(@Nullable Object v1, @Nullable Object v2, String msg) {
        if (!Objects.equal(v1, v2)) {
            throw new IllegalStateException(msg);
        }
    }

    @Nonnull
    public static <T> T assertNotNull(@Nullable T value, String msg) {
        if (value == null) {
            throw new IllegalStateException(msg);
        }
        return value;
    }

    public static void assertNull(@Nullable Object value, String msg) {
        if (value != null) {
            throw new IllegalStateException(msg);
        }
    }

    public static void assertParcelable(Parcelable parcelable) {
        Parcel p = Parcel.obtain();
        parcelable.writeToParcel(p, 0);
        byte[] val = p.marshall();
        p.unmarshall(val, 0, val.length);
        p.readParcelable(OSAssert.class.getClassLoader());
        // Parcelable parcelable2 = p.readParcelable(null);
        // if (!parcelable.equals(parcelable2)) {
        // throw new IllegalStateException("Parcelable equality check failed");
        // }
        p.recycle();
    }

    public static void assertMonitorHeld(Monitor monitor) {
        if (!monitor.isOccupiedByCurrentThread()) {
            throw new IllegalStateException("assertion failed: expected monitor to be held");
        }
    }
}
