/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.google.common.base.Objects;
import com.google.common.io.BaseEncoding;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.orangebikelabs.orangesqueeze.common.Actions;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.ServerType;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.database.Server;
import com.orangebikelabs.orangesqueeze.database.ServerQueries;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;


/**
 * class is final to satisfy our strict bus registrant checks.
 *
 * @author tbsandee@orangebikelabs.com
 */
final public class DiscoveryService extends AbstractExecutionThreadService {
    public static final String ACTION_TRIGGER_DISCOVERY = Actions.COMMON_ACTIONS_PREFIX + ".TRIGGER_DISCOVERY";

    public static final String ACTION_NOTIFY_DISCOVERED_SERVER = Actions.COMMON_ACTIONS_PREFIX + ".NOTIFY_DISCOVERED_SERVER";

    public static final String PARAM_DISCOVERED_SERVER_NAME = Actions.COMMON_EXTRAS_PREFIX + ".DiscoveredServerName";
    public static final String PARAM_DISCOVERED_SERVER_IP = Actions.COMMON_EXTRAS_PREFIX + ".DiscoveredServerIp";
    public static final String PARAM_DISCOVERED_SERVER_JSONPORT = Actions.COMMON_EXTRAS_PREFIX + ".DiscoveredServerJsonPort";

    public static final int DISCOVERY_PORT = 3483;
    public static final long MAX_RETRY_INTERVAL = 60; // seconds
    public static final long MIN_RETRY_INTERVAL = 10; // seconds

    static public void triggerDiscovery(Context context) {
        Intent discoveryIntent = new Intent(ACTION_TRIGGER_DISCOVERY);
        context.sendBroadcast(discoveryIntent);
    }

    @Nonnull
    final private Context mContext;

    @Nonnull
    final private TriggerDiscoveryReceiver mTriggerDiscoveryReceiver;

    // accessed from service thread only
    private ScheduledFuture<?> mScheduledDiscoveryTask;

    volatile private boolean mConnected;

    @GuardedBy("this")
    @Nullable
    private DatagramSocket mSocket;

    public DiscoveryService(Context context) {
        mContext = context;
        mTriggerDiscoveryReceiver = new TriggerDiscoveryReceiver();
    }

    @Override
    protected void triggerShutdown() {
        super.triggerShutdown();

        // initiate closeSocket, a more reliable way to interrupt this service
        closeSocket();
    }

    @Override
    protected void shutDown() {
        mContext.unregisterReceiver(mTriggerDiscoveryReceiver);

        if (mScheduledDiscoveryTask != null) {
            mScheduledDiscoveryTask.cancel(true);
        }

        closeSocket();
    }

    @Override
    protected void startUp() {
        IntentFilter filter = new IntentFilter(ACTION_TRIGGER_DISCOVERY);
        mContext.registerReceiver(mTriggerDiscoveryReceiver, filter);

        mScheduledDiscoveryTask = OSExecutors.getSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> triggerDiscovery(mContext), 5, Constants.DISCOVERY_PACKET_INTERVAL, Constants.TIME_UNITS);
    }

    @Override
    protected void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

        long sleepTime = MIN_RETRY_INTERVAL;
        while (isRunning()) {
            mConnected = false;
            try {
                establishConnection();
                mConnected = true;

                sleepTime = MIN_RETRY_INTERVAL;
                processLoop();
            } catch (BindException e) {
                OSLog.i(Tag.NETWORK, "Squeezebox Server Discovery: Problem binding to UDP port " + DISCOVERY_PORT + ", waiting and trying again in "
                        + sleepTime + " seconds", e);
            } catch (IOException e) {
                OSLog.w(Tag.NETWORK, "Error creating UDP socket, waiting and trying again", e);
            } finally {
                closeSocket();
            }

            if (!mConnected && isRunning()) {
                // sleep for a bit before we retry
                try {
                    // but no longer than MAX_RETRY_INTERVAL
                    sleepTime = Math.min(MAX_RETRY_INTERVAL, sleepTime);
                    Thread.sleep(TimeUnit.SECONDS.toMillis(sleepTime));
                    sleepTime *= 2;
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            mConnected = false;
        }
    }

    private void establishConnection() throws IOException {
        DatagramSocket newSocket = new DatagramSocket(DISCOVERY_PORT);
        newSocket.setSoTimeout((int) Constants.TIME_UNITS.toMillis(Constants.READ_TIMEOUT));
        setSocket(newSocket);
    }

    private void processLoop() {
        OSLog.d(Tag.NETWORK, "Discovery service started");
        try {
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (isRunning()) {
                try {
                    // work around ICS bug and set the packet length
                    packet.setLength(buf.length);

                    if (receive(packet)) {
                        processPacket(packet, buf);
                    }
                } catch (InterruptedIOException e) {
                    // ignore timeout exceptions always, this includes socket timeout exception
                }
            }
        } catch (IOException e) {
            // only log IOException if it occurs while running
            if (isRunning()) {
                OSLog.e(Tag.NETWORK, "Error with udp socket", e);
            }
        }

    }

    private void processPacket(DatagramPacket packet, byte[] buf) throws IOException {
        logPacket(packet, buf);

        final int offset = packet.getOffset();
        if (buf[offset] == 'E') {
            String ipaddress = packet.getAddress().getHostAddress();
            String servername = null;
            int jsonport = Constants.DEFAULT_SERVER_PORT;

            int i = offset + 1;
            int packetLength = offset + packet.getLength();
            while (packetLength >= i + 5) {
                String tag = new String(buf, i, 4, "US-ASCII");
                int length = buf[i + 4] & 0xFF;
                i += 5;

                String value = new String(buf, i, Math.min(length, packetLength - i), "UTF-8");
                i += length;

                switch (tag) {
                    case "IPAD":
                        ipaddress = value;
                        break;
                    case "NAME":
                        servername = value;
                        break;
                    case "JSON":
                        jsonport = parsePort(value, jsonport);
                        break;
                    default:
                        // ignore;
                        break;
                }
            }
            if (servername != null) {
                Intent discoveryIntent = new Intent(ACTION_NOTIFY_DISCOVERED_SERVER);
                discoveryIntent.putExtra(PARAM_DISCOVERED_SERVER_NAME, servername);
                discoveryIntent.putExtra(PARAM_DISCOVERED_SERVER_IP, ipaddress);
                discoveryIntent.putExtra(PARAM_DISCOVERED_SERVER_JSONPORT, jsonport);
                mContext.sendBroadcast(discoveryIntent);
            }
        } else {
            // ignore UDP packets that aren't discovery
        }
    }

    private int parsePort(String value, int defaultPort) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            OSLog.w(Tag.NETWORK, "Invalid JSON port in discovery packet: " + value);
            return defaultPort;
        }
    }

    private void logPacket(DatagramPacket packet, byte[] buf) {
        if (OSLog.isLoggable(Tag.NETWORK, OSLog.VERBOSE)) {
            OSLog.v(Tag.NETWORK, "udp recv: " + BaseEncoding.base16().encode(buf, packet.getOffset(), packet.getLength()));
        }
    }

    synchronized private void setSocket(DatagramSocket socket) {
        mSocket = socket;
    }

    synchronized private void closeSocket() {
        if (mSocket != null) {
            mSocket.close();
            mSocket = null;
        }
    }

    @Nullable
    synchronized private DatagramSocket getSocket() {
        return mSocket;
    }

    private void send(DatagramPacket p) throws IOException {
        DatagramSocket socket = getSocket();
        if (socket != null) {
            socket.send(p);
        }
    }

    private boolean receive(DatagramPacket p) throws IOException {
        boolean retval = false;
        DatagramSocket socket = getSocket();
        if (socket != null) {
            retval = true;
            socket.receive(p);
        }
        return retval;
    }

    private void internalSendDiscoveryPacket() {
        try {
            String discovery = "eIPAD\0NAME\0JSON\0";
            byte[] bytes = discovery.getBytes("US-ASCII");
            InetAddress broadcast = InetAddresses.forString("255.255.255.255");
            DatagramPacket p = new DatagramPacket(bytes, bytes.length, broadcast, 3483);
            send(p);
        } catch (IOException e) {
            OSLog.e(Tag.NETWORK, "Error sending discovery broadcast packet", e);
        }
    }

    private class TriggerDiscoveryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, @Nullable Intent intent) {
            if (intent == null || !Objects.equal(intent.getAction(), DiscoveryService.ACTION_TRIGGER_DISCOVERY)) {
                return;
            }

            OSExecutors.getSingleThreadScheduledExecutor().execute(() -> {
                if (!isRunning()) {
                    return;
                }

                if (mConnected) {
                    internalSendDiscoveryPacket();
                }
            });
        }
    }

    public static class ServerDiscoveredReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, @Nullable Intent intent) {
            if (intent == null || !Objects.equal(intent.getAction(), DiscoveryService.ACTION_NOTIFY_DISCOVERED_SERVER)) {
                return;
            }

            final String name = intent.getStringExtra(DiscoveryService.PARAM_DISCOVERED_SERVER_NAME);
            final String ipaddress = intent.getStringExtra(DiscoveryService.PARAM_DISCOVERED_SERVER_IP);
            final int jsonport = intent.getIntExtra(DiscoveryService.PARAM_DISCOVERED_SERVER_JSONPORT, 0);

            if (name == null || ipaddress == null || jsonport == 0) {
                // invalid data
                OSLog.w("Dropped invalid discovery packet name=" + name + ", ip=" + ipaddress + ", port=" + jsonport);
                return;
            }

            // do bulk of work on a background thread, since these queries aren't exactly atomic (query followed by insert/update), do
            // it all using the single thread executor
            final Context applicationContext = context.getApplicationContext();
            OSAssert.assertNotNull(applicationContext, "application context should always be valid");

            OSExecutors.getSingleThreadScheduledExecutor().execute(() -> {
                ServerQueries sq = DatabaseAccess.getInstance(applicationContext)
                        .getServerQueries();

                Server existingServer = sq.lookupByName(name)
                        .executeAsOneOrNull();
                if (existingServer == null) {
                    sq.insertDiscovered(ipaddress, jsonport, System.currentTimeMillis(), name, ServerType.DISCOVERED);
                } else {
                    // don't touch pinned servers or mysqueezebox.com
                    if (ServerType.DISCOVERED.equals(existingServer.getServertype())) {
                        sq.updateDiscovered(ipaddress, jsonport, System.currentTimeMillis(), existingServer.get_id());
                    }
                }
            });
        }
    }
}