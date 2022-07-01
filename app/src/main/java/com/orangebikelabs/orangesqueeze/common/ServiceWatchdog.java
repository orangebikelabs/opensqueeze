/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.os.SystemClock;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Uninterruptibles;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * This class has the additional feature of allowing the caller to monitor the state of the current
 * service by supplying a Monitor object.
 *
 * @author tsandee
 */
abstract public class ServiceWatchdog<T extends Service> extends AbstractService {
    @GuardedBy("this")
    @Nullable
    private T mRunningService;

    @GuardedBy("this")
    private int mRetryCount = 10;

    @GuardedBy("this")
    private boolean mFailed;

    public ServiceWatchdog(@Nullable T initialValue) {
        mRunningService = initialValue;
    }

    public ServiceWatchdog() {
        this(null);
    }

    protected void onServiceChanged(@Nullable T oldValue, @Nullable T newValue) {
        // no implementation
    }

    @Override
    protected void doStart() {
        T service = getRunningService();
        if (service != null) {
            if (service.state() == State.RUNNING || service.state() == State.STARTING) {
                // already started, just attach to existing service
                onServiceChanged(null, service);

                service.addListener(new Listener(service, getRetryCount()), MoreExecutors.directExecutor());
            } else {
                service = null;
            }
        }
        if (service == null) {
            service = newService();
            service.addListener(new Listener(service, getRetryCount()), MoreExecutors.directExecutor());
            service.startAsync();
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        T service = getRunningService();
        if (service != null) {
            service.stopAsync();
        }
        notifyStopped();
    }

    @Nonnull
    abstract protected T newService();

    @Nullable
    synchronized public T getRunningService() {
        return mRunningService;
    }

    protected void setRunningService(@Nullable T service) {
        T oldValue;
        synchronized (this) {
            oldValue = mRunningService;
            mRunningService = service;
        }

        if (oldValue != service) {
            onServiceChanged(oldValue, service);
        }
    }

    synchronized public boolean isFailed() {
        return mFailed;
    }

    synchronized private void setFailed(boolean failed) {
        mFailed = failed;
    }

    protected synchronized int getRetryCount() {
        return mRetryCount;
    }

    protected synchronized void setRetryCount(int retryCount) {
        mRetryCount = retryCount;
    }

    static final private long RETRY_DELAY_MILLIS = 5000;

    private class Listener extends Service.Listener {
        final private long mNextEarliestRetryTime;
        final private T mMonitorService;
        volatile private int mRemainingRetries;

        Listener( T monitorService, int remainingRetries) {
            mMonitorService = monitorService;
            mRemainingRetries = remainingRetries;
            mNextEarliestRetryTime = SystemClock.uptimeMillis() + RETRY_DELAY_MILLIS;
        }

        @Override
        public void terminated(State state) {
            // service stopped itself without a failure condition
            triggerRestart(null);
        }

        @Override
        public void stopping(State state) {
            // no implementation
        }

        @Override
        public void starting() {
            // no implementation
        }

        @Override
        public void running() {
            // once service is running, reset retry count
            mRemainingRetries = getRetryCount();
            ServiceWatchdog.this.setRunningService(mMonitorService);
        }

        @Override
        public void failed(State state, Throwable t) {
            // service crashed due to failure condition
            if (t instanceof Exception) {
                triggerRestart(t);
            } else {
                throw new RuntimeException(t);
            }
        }

        synchronized protected void triggerRestart(@Nullable Throwable reason) {
            State state = state();
            if (state != State.RUNNING && state != State.STARTING) {
                // don't restart
                return;
            }
            if(mRemainingRetries <= 0) {
                // don't restart
                setFailed(true);
                return;
            }
            long elapsed = mNextEarliestRetryTime - SystemClock.uptimeMillis();
            if (elapsed > 0) {
                OSLog.w("Service restart retries remaining " + mRemainingRetries + ", waiting " + elapsed, reason);
                Uninterruptibles.sleepUninterruptibly(elapsed, TimeUnit.MILLISECONDS);
            }
            T service = newService();
            service.addListener(new Listener(service, mRemainingRetries - 1), MoreExecutors.directExecutor());
            service.startAsync();
        }
    }
}
