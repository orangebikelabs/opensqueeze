/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.content.Context;

import com.google.common.reflect.Reflection;
import com.google.common.util.concurrent.Atomics;
import com.orangebikelabs.orangesqueeze.app.ContextImpl;
import com.orangebikelabs.orangesqueeze.app.NoopContextImpl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

/**
 * @author tsandee
 */
public class SBContextProvider {

    final private static AtomicReference<ContextImpl> sInstance = Atomics.newReference();
    final private static NoopContextImpl sNoopContext = new NoopContextImpl();

    @Nonnull
    static public SBContext get() {
        SBContext retval = sInstance.get();
        if (retval == null) {
            retval = sNoopContext;
        }
        return retval;
    }

    @Nonnull
    static public SBContext initializeAndGet(Context context) {
        OSAssert.assertMainThread();
        ContextImpl retval = sInstance.get();

        // because this is only called from the main thread, no worries about double-checked locking or concurrency
        if (retval == null) {
            Context applicationContext = context.getApplicationContext();
            OSAssert.assertNotNull(applicationContext, "application context should never be null");
            retval = new ContextImpl(applicationContext);
            sInstance.set(retval);
        }
        return retval;
    }

    static public SBContext uninitialized() {
        return sNoopContext;
    }

    static public SBContextWrapper mutableWrapper(SBContext base) {
        return Reflection.newProxy(SBContextWrapper.class, new MutableHandler(base));
    }

    private SBContextProvider() {
        // no instances
    }

    static private class MutableHandler implements InvocationHandler {
        @GuardedBy("this")
        private SBContext mBase;

        protected MutableHandler(SBContext base) {
            mBase = base;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("setBase")) {
                setBase((SBContext) args[0]);
                return null;
            } else {
                try {
                    return method.invoke(getBase(), args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }

        synchronized private SBContext getBase() {
            return mBase;
        }

        synchronized private void setBase(SBContext base) {
            mBase = base;
        }
    }
}
