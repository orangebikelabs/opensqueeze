/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.os.Handler;
import android.os.Looper;

import com.google.common.collect.Lists;
import com.orangebikelabs.orangesqueeze.BuildConfig;

import java.lang.reflect.Modifier;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * Returns a slight variant to the Otto Bus. This enforces some constraints on objects during registration and also allows posting from any
 * thread (but delivers notifications on main thread).
 *
 * @author tsandee
 */
public class BusProvider {
    /**
     * in case class is initialized on non-main thread
     */
    final protected static Handler sHandler = new Handler(Looper.getMainLooper());
    final protected static com.squareup.otto.Bus sBackingInstance = new com.squareup.otto.Bus(com.squareup.otto.ThreadEnforcer.MAIN);
    final protected static Bus sInstance = new Bus() {
        @Override
        public void register(final Object o) {
            OSAssert.assertMainThread();

            assertRegisterObjectValid(o);

            sBackingInstance.register(o);
        }

        @Override
        public void unregister(final Object o) {
            OSAssert.assertMainThread();
            sBackingInstance.unregister(o);
        }

        @Override
        public void post(final Object event) {
            if (!ThreadTools.isMainThread()) {
                sHandler.post(() -> postFromMain(event));
            } else {
                postFromMain(event);
            }
        }

        @Override
        public void postFromMain(Object event) {
            if (OSLog.isLoggable(OSLog.DEBUG)) {
                OSLog.d("Bus Event = " + event);
            }
            OSAssert.assertMainThread();

            sBackingInstance.post(event);
        }
    };

    @Nonnull
    public static Bus getInstance() {
        return sInstance;
    }

    @Nonnull
    public static ScopedBus newScopedInstance() {
        return new ScopedBus();
    }

    static protected void assertRegisterObjectValid(Object o) {
        // more of an assertion
        if (BuildConfig.DEBUG) {
            int modifiers = o.getClass().getModifiers();
            if (!Modifier.isFinal(modifiers) && !o.getClass().getSuperclass().equals(Object.class)) {
                throw new IllegalArgumentException("non-final object passed to Bus.register()");
            }
        }
    }

    // don't create this
    private BusProvider() {
    }

    /**
     * scoped bus starts out inactive, to be activated by call to start()
     */
    static public class ScopedBus implements Bus {
        @Nonnull
        private final List<Object> mObjects = Lists.newCopyOnWriteArrayList();
        private boolean mActive = false;

        ScopedBus() {
        }

        @Override
        public void register(Object obj) {
            OSAssert.assertMainThread();

            assertRegisterObjectValid(obj);

            mObjects.add(obj);
            if (mActive) {
                sInstance.register(obj);
            }
        }

        @Override
        public void unregister(Object obj) {
            OSAssert.assertMainThread();
            boolean found = mObjects.remove(obj);
            OSAssert.assertTrue(found, "Unexpected missing unregister instance for type " + obj.getClass().getName());
            if (mActive) {
                sInstance.unregister(obj);
            }
        }

        @Override
        public void postFromMain(Object event) {
            sInstance.postFromMain(event);
        }

        @Override
        public void post(Object event) {
            sInstance.post(event);
        }

        public void stop() {
            OSAssert.assertMainThread();

            if (!mActive) {
                Reporting.reportIfDebug("BusProvider::stop() activeBefore=false");
            }

            boolean activeBefore = mActive;

            mActive = false;
            for (Object obj : mObjects) {
                try {
                    sInstance.unregister(obj);
                } catch (IllegalArgumentException e) {
                    Reporting.reportIfDebug(e, "ISE BusProvider::stop() activeBefore=" + activeBefore);
                }
            }
        }

        public void start() {
            OSAssert.assertMainThread();

            boolean activeBefore = mActive;
            if (mActive) {
                Reporting.reportIfDebug("BusProvider::start() activeBefore=true");
            }
            mActive = true;
            for (Object obj : mObjects) {
                try {
                    sInstance.register(obj);
                } catch (IllegalArgumentException e) {
                    Reporting.reportIfDebug(e, "ISE BusProvider::start() activeBefore=" + activeBefore);
                }
            }
        }
    }
}
