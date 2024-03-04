/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.compat.Compat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class NetworkTools {
    public static final ImmutableList<Integer> DEFAULT_WAKEONLAN_PORTS = ImmutableList.of(7, 9);

    /**
     * send a wake-on-lan packet for the supplied connection data. If the connection has disabled or otherwise misconfigured wake-on-lan,
     * this method will silently fail
     */
    static public void sendWakeOnLan(Context context, ConnectionInfo ci) {
        // are we doing WOL?
        if (ci.getWakeOnLanSettings().wakeDuringConnection()) {
            // only send wol if we have a mac address and !squeezenetwork
            String macAddress = ci.getWakeOnLanSettings().getMacAddress();

            if (macAddress != null && !macAddress.equals("")) {
                try {

                    InetAddress broadcast;

                    // this isn't really autodetecting the mac address, we changed the checkbox name but keep using the old property
                    if (ci.getWakeOnLanSettings().getAutodetectMacAddress()) {
                        broadcast = Compat.getBroadcastAddress(context).orElse(null);
                    } else {
                        broadcast = InetAddress.getByName(ci.getWakeOnLanSettings().getBroadcastAddress());
                    }
                    if (broadcast != null) {
                        NetworkTools.sendWakeOnLan(context, broadcast, macAddress, ci.getWakeOnLanSettings().getPorts());
                    }
                } catch (UnknownHostException e) {
                    OSLog.w(Tag.NETWORK, "Unknown Wake-On-LAN host", e);
                } catch (IOException e) {
                    OSLog.w(Tag.NETWORK, "Network error performing WOL", e);
                }
            }
        }
    }

    /**
     * do a best-effort to retrieve the MAC address of the current device
     */
    @SuppressLint({"HardwareIds", "MissingPermission"})
    @Nonnull
    public static String getMacAddress(Context context) {
        String retval = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            retval = marshmallowGetMacAddress();
        } else {
            WifiManager wfm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            OSAssert.assertNotNull(wfm, "wifimanager can't be null");

            WifiInfo wfi = wfm.getConnectionInfo();
            if (wfi != null) {
                return wfi.getMacAddress();
            }
        }
        if (retval == null) {
            retval = "aa:bb:cc:dd:ee:ff";
        }
        return retval;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Nullable
    private static String marshmallowGetMacAddress() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    continue;
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(Integer.toHexString(b & 0xFF));
                    res1.append(":");
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) {
            // ignore
        }
        return null;
    }

    /**
     * This code is based on code from here:
     * <a href="http://www.flattermann.net/2011/02/android-howto-find-the-hardware-mac-address-of-a-remote-host/">...</a>
     * <p/>
     * Try to extract a hardware MAC address from a given IP address using the ARP cache (/proc/net/arp).<br>
     * <br>
     * We assume that the file has this structure:<br>
     * <br>
     * IP address HW type Flags HW address Mask Device 192.168.18.11 0x1 0x2 00:04:20:06:55:1a * eth0 192.168.18.36 0x1 0x2
     * 00:22:43:ab:2a:5b * eth0
     *
     * @param ip the ip of the hostname
     * @return the MAC from the ARP cache
     */
    @Nullable
    public static String getMacFromArpCache(@Nullable final String ip) {
        if (ip == null || Build.VERSION.SDK_INT >= 28) {
            return null;
        }

        String retval = null;
        try {
            retval = Files.asCharSource(new File("/proc/net/arp"), Charsets.UTF_8).readLines(new LineProcessor<>() {
                private String mResult;

                @Override
                public String getResult() {
                    return mResult;
                }

                @Override
                public boolean processLine(String line) {
                    String[] splitted = line.split(" +");
                    if (splitted.length >= 4 && ip.equals(splitted[0])) {
                        // Basic sanity check
                        String mac = splitted[3];
                        if (mac.matches("..:..:..:..:..:..")) {
                            mResult = mac;
                        } else {
                            OSLog.w("No MAC address detected in arp cache line: " + line);
                        }
                    }
                    return mResult == null;
                }
            });
        } catch (FileNotFoundException e) {
            OSLog.w(Tag.NETWORK, "No ARP lookup file found", e);
        } catch (Exception e) {
            OSLog.e(Tag.NETWORK, "Error inspecting ARP file", e);
        }
        return retval;
    }

    /**
     * utility method to send wake on lan packet for the specified server mac address, on the supplied port
     */
    public static void sendWakeOnLan(Context context, InetAddress broadcast, String macAddress, List<Integer> ports) {
        try {
            OSLog.i(Tag.NETWORK, "Sending WOL packets on broadcast address: " + broadcast + ", MAC address: " + macAddress + ", ports: " + ports);

            // IMPORTANT NOTE: DatagramSocket does not implement Closeable on older platforms!
            DatagramSocket socket = new DatagramSocket();
            try {
                for (int port : ports) {
                    sendWOLNew(socket, broadcast, macAddress, port);
                    sendWOLOld(socket, broadcast, macAddress, port);
                }
            } finally {
                socket.close();
            }
        } catch (IOException | MacAddressException e) {
            OSLog.w(Tag.NETWORK, "Error sending WOL packet", e);
        }
    }

    /**
     * worker method that sends the WOL packet to the specified mac using the supplied broadcast address
     */
    private static void sendWOLNew(DatagramSocket socket, InetAddress broadcast, String mac, int port) throws IOException, MacAddressException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);

        byte[] macBytes = getMacBytes(mac);
        for (int i = 0; i < 6; i++) {
            baos.write((byte) 0xff);
        }
        baos.write(macBytes);

        baos.write((byte) 0x08);
        baos.write((byte) 0x42);

        for (int i = 0; i < 6; i++) {
            baos.write((byte) 0xff);
        }

        for (int i = 0; i < 16; i++) {
            baos.write(macBytes);
        }
        socket.send(new DatagramPacket(baos.toByteArray(), baos.size(), broadcast, port));
    }

    /**
     * worker method that sends the WOL packet to the specified mac using the supplied broadcast address
     */
    private static void sendWOLOld(DatagramSocket socket, InetAddress broadcast, String mac, int port) throws IOException, MacAddressException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(128);

        byte[] macBytes = getMacBytes(mac);
        for (int i = 0; i < 6; i++) {
            baos.write((byte) 0xff);
        }
        for (int i = 0; i < 16; i++) {
            baos.write(macBytes);
        }
        socket.send(new DatagramPacket(baos.toByteArray(), baos.size(), broadcast, port));
    }

    /**
     * convert the supplied mac address to a byte array
     */
    private static byte[] getMacBytes(String macStr) throws MacAddressException {
        byte[] bytes = new byte[6];
        String[] hex = macStr.split("([:-])");
        if (hex.length != 6) {
            throw new MacAddressException("Invalid MAC address");
        }
        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
        } catch (NumberFormatException e) {
            throw new MacAddressException("Invalid hex digit in MAC address", e);
        }
        return bytes;
    }

    static class MacAddressException extends Exception {

        public MacAddressException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public MacAddressException(String detailMessage) {
            super(detailMessage);
        }
    }

}
