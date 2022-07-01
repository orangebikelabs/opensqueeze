/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.util.concurrent.FutureCallback;
import com.orangebikelabs.orangesqueeze.BuildConfig;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for closeables.
 */
public class Closeables {

    final static private FutureCallback<Closeable> sCloserCallback = new FutureCallback<Closeable>() {
        @Override
        public void onSuccess(@Nullable Closeable closeable) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }

        @Override
        public void onFailure(@Nullable Throwable throwable) {
            // ignore
        }
    };

    /**
     * attempt to close the object, if it's closeable
     */
    public static void close(@Nullable Object o) throws IOException {
        if (o instanceof Closeable) {
            ((Closeable) o).close();
        }
    }

    @Nonnull
    static public FutureCallback<Closeable> getCloserCallback() {
        return sCloserCallback;
    }

    /**
     * Utility class that, in debug mode, returns a closeable that detects if it remains unclosed when the class instance is finalized.
     */
    @Nonnull
    static public Closeable newCloseTracker() {
        if (BuildConfig.DEBUG) {
            return new CloseTracker();
        } else {
            return sNoopTracker;
        }
    }

    final static private Closeable sNoopTracker = () -> {
    };

    private static class CloseTracker implements Closeable {
        @Nonnull
        final private Exception mException;

        @Nonnull
        final private AtomicBoolean mClosed = new AtomicBoolean(false);

        private CloseTracker() {
            mException = new Exception();
        }


        @Override
        public void close() throws IOException {
            if (mClosed.getAndSet(true)) {
                OSLog.w("Close tracker detected multiple calls to close, allocated from this stack trace", mException);
                throw new IllegalStateException("Detected multiple calls to close");
            }
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();

            if (!mClosed.getAndSet(true)) {
                OSLog.w("Close tracker detected a missing call to close, allocated from this stack trace", mException);
            }
        }
    }

    private Closeables() {
    }
}
