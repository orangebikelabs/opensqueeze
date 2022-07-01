/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.os.Looper;

/**
 * Threading-related tools.
 */
public class ThreadTools {
    /**
     * @return true if the current thread is the main thread
     */
    public static boolean isMainThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    public static boolean isSingleSchedulingThread() {
        return Thread.currentThread().getName().equals(OSExecutors.SINGLE_SCHEDULED_THREAD_NAME);
    }
}
