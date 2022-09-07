/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.compat;

import android.app.PendingIntent;
import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;

import com.google.common.collect.Iterables;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;

import java.io.File;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.annotation.Nonnull;

import arrow.core.Option;

/**
 * Various helper functions that provide access to, to some extent, various APIs that are not universally available
 *
 * @author tbsandee@orangebikelabs.com
 */
public class Compat {

    public static int getDefaultPendingIntentFlags() {
        if (Build.VERSION.SDK_INT >= 23) {
            return PendingIntent.FLAG_IMMUTABLE;
        } else {
            return 0;
        }
    }

    @Nonnull
    public static List<File> getPublicMediaDirs() {
        ArrayList<File> retval = new ArrayList<>();
        retval.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC));
        retval.addAll(Arrays.asList(SBContextProvider.get().getApplicationContext().getExternalMediaDirs()));
        // remove nulls
        //noinspection Convert2MethodRef
        Iterables.removeIf(retval, (elem) -> elem == null);

        return retval;
    }

    public static long getTotalSpace(File f) {
        try {
            StatFs fs = new StatFs(f.getPath());
            return fs.getTotalBytes();
        } catch (IllegalArgumentException e) {
            // fallback
            return 0L;
        }
    }

    /**
     * retrieve the InetAddress broadcast address
     */
    public static Option<InetAddress> getBroadcastAddress(Context context) throws SocketException, UnknownHostException {
        InetAddress retval = internalGetBroadcastAdddress(context);
        if (retval == null) {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            OSAssert.assertNotNull(wifiManager, "wifimanager can't be null");

            DhcpInfo dhcp = wifiManager.getDhcpInfo();
            if (dhcp != null) {
                int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
                byte[] quads = new byte[4];
                for (int k = 0; k < 4; k++) {
                    quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
                }
                retval = InetAddress.getByAddress(quads);
            }
        }
        return Option.fromNullable(retval);
    }

    @Nonnull
    public static List<InetAddress> getLocalAddresses(Context context) {
        List<InetAddress> retval = null;
        try {
            retval = internalGetLocalAddresses();
        } catch (SocketException e) {
            OSLog.w("Determining local network interface (Gingerbread and higher)", e);
        }
        if (retval == null) {
            try {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                OSAssert.assertNotNull(wifiManager, "wifimanager can't be null");

                @SuppressWarnings("deprecation")
                String strAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                retval = Collections.singletonList(InetAddress.getByName(strAddress));
            } catch (UnknownHostException e) {
                OSLog.w("Determining local network interface (Pre-Gingerbread)", e);
                retval = Collections.emptyList();
            }
        }
        return retval;
    }

    /**
     * Returns list of local InetAddress values.
     */
    private static List<InetAddress> internalGetLocalAddresses() throws SocketException {
        List<InetAddress> retval = new ArrayList<>();
        Enumeration<NetworkInterface> localInterfaces = null;
        // ATTENTION
        // THIS try/catch block works around a rare bug in getNetworkInterfaces on some Android 4.0.x devices that cause it to throw an NPE occasionally
        try {
            localInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (NullPointerException e) {
            try {
                // try again
                localInterfaces = NetworkInterface.getNetworkInterfaces();
            } catch (NullPointerException e2) {
                // this time ignore...
            }
        }
        if (localInterfaces != null) {
            while (localInterfaces.hasMoreElements()) {
                NetworkInterface e = localInterfaces.nextElement();
                for (InterfaceAddress ia : e.getInterfaceAddresses()) {
                    InetAddress address = ia.getAddress();
                    if (address != null) {
                        retval.add(address);
                    }
                }
            }
        }
        return retval;
    }

    @SuppressWarnings("deprecation")
    private static InetAddress internalGetBroadcastAdddress(Context context) throws SocketException, UnknownHostException {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        OSAssert.assertNotNull(wifiManager, "wifimanager can't be null");

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();
        String ipAddress = Formatter.formatIpAddress(ipInt);
        InetAddress addr = InetAddress.getByName(ipAddress);

        NetworkInterface netInterface = NetworkInterface.getByInetAddress(addr);
        if (netInterface != null) {
            for (InterfaceAddress ia : netInterface.getInterfaceAddresses()) {
                InetAddress broadcast = ia.getBroadcast();
                if (broadcast != null) {
                    return broadcast;
                }
            }
        }
        return null;
    }

    /** this is only allowed on Android versions before 10 */
    public static boolean isArpLookupAllowed() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.P;
    }
}
