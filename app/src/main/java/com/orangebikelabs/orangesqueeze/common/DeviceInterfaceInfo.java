/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;

import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.common.event.ConnectivityChangeEvent;
import com.orangebikelabs.orangesqueeze.compat.Compat;
import com.squareup.otto.Subscribe;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides cached access to device interface information.
 *
 * @author tsandee
 */
@ThreadSafe
public class DeviceInterfaceInfo {

    @Nonnull
    final static protected Object sLock = new Object();

    @GuardedBy("sLock")
    @Nullable
    static private DeviceInterfaceInfo sInstance;

    final static private Object sEventReceiver = new Object() {
        @Subscribe
        public void whenConnectivityChanges(ConnectivityChangeEvent event) {
            clearInstance();
        }
    };

    final static private AtomicBoolean sEventReceiverRegistered = new AtomicBoolean(false);

    static protected void clearInstance() {
        synchronized (sLock) {
            sInstance = null;
        }
    }

    @Nonnull
    static public DeviceInterfaceInfo getInstance() {

        // register listener for connectivity broadcasts
        if (sEventReceiverRegistered.compareAndSet(false, true)) {
            OSExecutors.getMainThreadExecutor().execute(() -> BusProvider.getInstance().register(sEventReceiver));
        }

        synchronized (sLock) {
            DeviceInterfaceInfo retval = sInstance;

            // fill current interface info
            if (retval == null) {
                retval = new DeviceInterfaceInfo();
                sInstance = retval;
            }
            return retval;
        }
    }

    @Nonnull
    final public ImmutableList<InetAddress> mAddresses;

    /** this is only used to detect local squeezeplayer instances. There may be better ways to do this that don't rely on the mac. */
    @Nullable
    final public String mMacAddress;

    /** this is only used to present the device IP to the user during connection for diagnostic purposes. Might be more trouble than it's worth. */
    @Nullable
    final public String mIpAddress;

    @SuppressWarnings("deprecation")
    @SuppressLint({"HardwareIds", "MissingPermission"})
    private DeviceInterfaceInfo() {
        Context context = SBContextProvider.get().getApplicationContext();

        // don't hold reference to activity context
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        OSAssert.assertNotNull(wifiManager, "wifimanager can't be null");

        mAddresses = ImmutableList.copyOf(Compat.getLocalAddresses(context));
        if (Build.VERSION.SDK_INT < 29) {
            mMacAddress = wifiManager.getConnectionInfo().getMacAddress();
            int ipInt = wifiManager.getConnectionInfo().getIpAddress();
            if (ipInt == 0) {
                mIpAddress = null;
            } else {
                mIpAddress = Formatter.formatIpAddress(ipInt);
            }
        } else {
            mIpAddress = null;
            mMacAddress = null;
        }
    }
}
