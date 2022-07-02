/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;

import com.google.common.util.concurrent.Monitor;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.RefCount;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.ServiceWatchdog;
import com.orangebikelabs.orangesqueeze.common.event.ConnectivityChangeEvent;
import com.orangebikelabs.orangesqueeze.net.DeviceConnectivity;
import com.orangebikelabs.orangesqueeze.net.DiscoveryService;
import com.orangebikelabs.orangesqueeze.net.StreamingConnection;
import com.orangebikelabs.orangesqueeze.players.SqueezePlayerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/**
 * @author tsandee
 */
public class ContextLifecycleState {
    /**
     * refcount for base server connection
     */
    final private RefCount mBaseStarted = RefCount.newInstance("base", 0);

    /**
     * refcount for ui-driven components
     */
    final private RefCount mUiStarted = RefCount.newInstance("ui", 0);

    @Nonnull
    final private ContextImpl mMasterContext;

    @Nonnull
    final private DeviceConnectivity mDeviceConnectivity;

    @GuardedBy("mLifecycleMonitor")
    private boolean mContextStarted;

    @GuardedBy("mLifecycleMonitor")
    @Nullable
    private ServiceWatchdog<StreamingConnection> mConnectionWatchdog;

    @GuardedBy("mLifecycleMonitor")
    @Nullable
    private StreamingConnection mConnection;

    // periodically ping squeezeplayer service
    @GuardedBy("mLifecycleMonitor")
    @Nullable
    private SqueezePlayerPingService mSqueezePlayerPingService;

    @GuardedBy("mLifecycleMonitor")
    @Nullable
    private DiscoveryService mDiscoveryService;

    // these guard the mConnectionWatchdog object and the backing connection
    @Nonnull
    final private Monitor mLifecycleMonitor = new Monitor();

    @Nonnull
    final private Monitor.Guard mConnectionObjectAvailable = new Monitor.Guard(mLifecycleMonitor) {

        @Override
        public boolean isSatisfied() {
            // because the watchdog service is monitored by the same monitor (see constructor), this is a valid guard
            return mConnection != null && mConnection.isRunning();
        }
    };

    ContextLifecycleState(Context applicationContext, ContextImpl masterContext) {
        mMasterContext = masterContext;
        mDeviceConnectivity = DeviceConnectivity.Companion.getInstance();

        //noinspection ResultOfMethodCallIgnored
        mDeviceConnectivity.observeDeviceConnectivity()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setDeviceConnectivity);
    }

    final private List<String> mWhereList = Collections.synchronizedList(new ArrayList<>());

    @Nullable
    public StreamingConnection awaitConnectionObjectAvailable(String where, long time, TimeUnit units) throws InterruptedException {
        StreamingConnection retval = null;

        mWhereList.add(where);
        try {
            OSLog.d("awaitConnection: " + where);

            if (mLifecycleMonitor.getWaitQueueLength(mConnectionObjectAvailable) > 50) {
                InterruptedException exception = new InterruptedException("too busy " + mWhereList);
                throw exception;
            }

            if (mLifecycleMonitor.enterWhen(mConnectionObjectAvailable, time, units)) {
                try {
                    // should never happen, the connection is ready
                    if (mConnection == null) {
                        throw new IllegalStateException();
                    }
                    retval = mConnection;
                } finally {
                    mLifecycleMonitor.leave();
                }
            }
            return retval;
        } finally {
            mWhereList.remove(where);
        }
    }

    public void onStart(Context context) {
        OSAssert.assertMainThread();

        mLifecycleMonitor.enter();
        try {
            doStart(context, mBaseStarted.increment(), mUiStarted.increment());
        } finally {
            mLifecycleMonitor.leave();
        }
    }

    public void onStop(Context context) {
        OSAssert.assertMainThread();

        mLifecycleMonitor.enter();
        try {
            doStop(mBaseStarted.decrement(), mUiStarted.decrement());
        } finally {
            mLifecycleMonitor.leave();
        }
    }

    public void onServiceCreate(Context context) {
        OSAssert.assertMainThread();

        mLifecycleMonitor.enter();
        try {
            doStart(context, mBaseStarted.increment(), false);
        } finally {
            mLifecycleMonitor.leave();
        }
    }

    public void onServiceDestroy(Context context) {
        OSAssert.assertMainThread();

        mLifecycleMonitor.enter();
        try {
            doStop(mBaseStarted.decrement(), false);
        } finally {
            mLifecycleMonitor.leave();
        }
    }

    public void restartDiscoveryService() {
        mLifecycleMonitor.enter();
        try {
            stopDiscoveryService();
            startDiscoveryService();
        } finally {
            mLifecycleMonitor.leave();
        }
    }

    public void startConnections(@Nullable StreamingConnection existingConnection) {
        mLifecycleMonitor.enter();
        OSLog.i("Starting server connection");
        try {
            ServiceWatchdog<StreamingConnection> newWatchdog = new ServiceWatchdog<>(existingConnection) {
                @Override
                @Nonnull
                protected StreamingConnection newService() {
                    StreamingConnection connection = new StreamingConnection(mMasterContext);
                    connection.setSendWakeOnLan(true);
                    return connection;
                }

                @Override
                protected void onServiceChanged(@Nullable StreamingConnection oldValue, @Nullable StreamingConnection newValue) {
                    setActiveConnection(newValue);
                }

                @Override
                protected void doStop() {
                    if (isFailed()) {
                        // signal disconnect at higher level when failure occurs
                        mMasterContext.disconnect();
                    }

                    super.doStop();
                }
            };
            mConnectionWatchdog = newWatchdog;
            newWatchdog.startAsync();
        } finally {
            mLifecycleMonitor.leave();
        }
    }

    public void stopConnections() {
        mLifecycleMonitor.enter();
        try {
            ServiceWatchdog<StreamingConnection> watchdog = mConnectionWatchdog;
            if (watchdog != null) {
                OSLog.i("Stopping server connection");
                mConnectionWatchdog = null;
                watchdog.stopAsync();
            }
        } finally {
            mLifecycleMonitor.leave();
        }
    }

    private void setDeviceConnectivity(boolean networkConnection) {
        OSAssert.assertMainThread();

        // yes,
        mLifecycleMonitor.enter();
        try {
            if (networkConnection) {
                doStart(mMasterContext.getApplicationContext(), !mBaseStarted.isFree(), !mUiStarted.isFree());
            } else {
                doStop(false, false);
            }
        } finally {
            mLifecycleMonitor.leave();
        }

        BusProvider.getInstance().postFromMain(new ConnectivityChangeEvent(networkConnection));
    }

    private void setActiveConnection(@Nullable StreamingConnection conn) {
        mLifecycleMonitor.enter();
        try {
            mConnection = conn;
        } finally {
            mLifecycleMonitor.leave();
        }
    }

    @GuardedBy("mLifecycleMonitor")
    private void startDiscoveryService() {
        OSAssert.assertMonitorHeld(mLifecycleMonitor);
        if (SBPreferences.get().isAutoDiscoverEnabled()) {
            mDiscoveryService = new DiscoveryService(mMasterContext.getApplicationContext());
            mDiscoveryService.startAsync();

            // receive broadcasts from other instances (preview vs release)
            IntentFilter filter = new IntentFilter(DiscoveryService.ACTION_NOTIFY_DISCOVERED_SERVER);
            mMasterContext.getApplicationContext().registerReceiver(mOnServerDiscoveredReceiver, filter);
        }
    }

    @GuardedBy("mLifecycleMonitor")
    private void stopDiscoveryService() {
        OSAssert.assertMonitorHeld(mLifecycleMonitor);
        DiscoveryService discoveryService = mDiscoveryService;
        if (discoveryService != null) {
            mDiscoveryService = null;
            discoveryService.stopAsync();

            try {
                mMasterContext.getApplicationContext().unregisterReceiver(mOnServerDiscoveredReceiver);
            } catch (IllegalStateException e) {
                Reporting.report(e);
            }
        }
    }

    @GuardedBy("mLifecycleMonitor")
    private void doStart(Context context, boolean baseTransitionedToStarted, boolean uiTransitionedToStarted) {
        OSLog.i("ContextLifecycleState::doStart baseCount=" + mBaseStarted.count() + ", baseTransitionedToStarted=" + baseTransitionedToStarted + ", uiCount=" + mUiStarted.count() + ", uiTransitionedToStarted=" + uiTransitionedToStarted);

        try (OSLog.TimingLoggerCompat timing = OSLog.Tag.TIMING.newTimingLogger("ContextLifecycleState::doStart")) {
            OSAssert.assertMonitorHeld(mLifecycleMonitor);

            if (baseTransitionedToStarted) {
                CacheServiceProvider.get().start();
            }

            if (uiTransitionedToStarted) {
                boolean squeezePlayerStarted = SqueezePlayerHelper.conditionallyStartService(mMasterContext.getApplicationContext());
                PlayerId playerId = mMasterContext.getPlayerId();
                if (playerId != null && squeezePlayerStarted && playerId.equals(SBPreferences.get().getLastConnectedSqueezePlayerId())) {
                    mMasterContext.setAutoSelectSqueezePlayer(true);
                }
            }

            boolean eligibleToStart = !mContextStarted && mDeviceConnectivity.getDeviceConnectivity();
            if (eligibleToStart && baseTransitionedToStarted) {
                mContextStarted = true;
                OSLog.d("Starting base connection");
                if (mMasterContext.isConnected()) {
                    startConnections(null);
                }
            }

            // if overall context is now started, and ui has just transitioned
            if (mContextStarted && uiTransitionedToStarted) {
                OSLog.d("Starting UI-level connection");
                startDiscoveryService();

                mSqueezePlayerPingService = new SqueezePlayerPingService();
                mSqueezePlayerPingService.startAsync();
            }
        }
    }

    @GuardedBy("mLifecycleMonitor")
    private void doStop(boolean baseTransitionedToStopped, boolean uiTransitionedToStopped) {
        OSLog.i("ContextLifecycleState::doStop newBaseCount=" + mBaseStarted.count() + ", baseTransitionedToStop=" + baseTransitionedToStopped + ", uiCount=" + mUiStarted.count() + ", uiTransitionedToStop=" + uiTransitionedToStopped);

        try (OSLog.TimingLoggerCompat timing = OSLog.Tag.TIMING.newTimingLogger("ContextLifecycleState::doStop")) {
            OSAssert.assertMonitorHeld(mLifecycleMonitor);

            boolean eligibleToStop = mContextStarted;

            if (eligibleToStop && (!mDeviceConnectivity.getDeviceConnectivity() || baseTransitionedToStopped || (uiTransitionedToStopped && mBaseStarted.isFree()))) {
                OSLog.d("Stopping base connection");
                mContextStarted = false;
                stopConnections();
            }

            // if context was stopped above for any reason, or if the UI just transitioned to stopped then stop these things
            if (!mContextStarted || uiTransitionedToStopped) {
                OSLog.d("Stopping UI-level connection");
                stopDiscoveryService();

                if (mSqueezePlayerPingService != null) {
                    mSqueezePlayerPingService.stopAsync();
                    mSqueezePlayerPingService = null;
                }
            }

            if (baseTransitionedToStopped) {
                CacheServiceProvider.get().stop();
            }
        }
    }

    @Nonnull
    final private BroadcastReceiver mOnServerDiscoveredReceiver = new DiscoveryService.ServerDiscoveredReceiver();
}
