/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides information about a connection to a squeezebox server. The instances are intended to be immutable.
 *
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
@Keep
public class ConnectionInfo implements Parcelable {
    final public static long INVALID_SERVER_ID = -1;

    @Nonnull
    public static ConnectionInfo newDisconnected() {
        WakeOnLanSettings wolSettings = new WakeOnLanSettings();
        wolSettings.setMode(WakeOnLanSettings.Mode.NEVER);
        return new ConnectionInfo(false, INVALID_SERVER_ID, "localhost", 9000, "locahost", null, null, wolSettings);
    }

    @Nonnull
    public static ConnectionInfo newInstance(boolean connected, long serverId, String serverHost, int serverPort, String serverName,
                                             @Nullable String username, @Nullable String password, @Nullable WakeOnLanSettings wolSettings) {
        if (wolSettings == null) {
            wolSettings = new WakeOnLanSettings();
            wolSettings.setMode(WakeOnLanSettings.Mode.NEVER);
        }

        return new ConnectionInfo(connected, serverId, serverHost, serverPort, serverName, username, password, wolSettings);
    }

    final protected long mServerId;

    @Nullable
    final protected String mServerHost;
    final protected int mServerPort;

    @Nullable
    final protected String mServerName;
    final protected boolean mSqueezeNetwork;

    @Nullable
    final protected String mUsername;

    @Nullable
    final protected String mPassword;

    @GuardedBy("this")
    protected boolean mConnected;

    @Nonnull
    final protected WakeOnLanSettings mWolSettings;

    protected ConnectionInfo(boolean connected, long serverId, @Nullable String serverHost, int serverPort, @Nullable String serverName,
                             @Nullable String username, @Nullable String password, WakeOnLanSettings wolSettings) {
        mServerId = serverId;
        mServerHost = serverHost;
        mServerPort = serverPort;
        mServerName = serverName;
        mUsername = username;
        mPassword = password;
        mConnected = connected;
        mWolSettings = wolSettings;

        boolean squeezeNetwork = false;
        if (serverHost != null) {
            for (String d : Constants.SQUEEZENETWORK_DOMAINS) {
                if (serverHost.endsWith(d)) {
                    squeezeNetwork = true;
                    break;
                }
            }
        }
        mSqueezeNetwork = squeezeNetwork;
    }

    @Nonnull
    public WakeOnLanSettings getWakeOnLanSettings() {
        return mWolSettings;
    }

    synchronized public void setConnected(boolean connected) {
        mConnected = connected;
    }

    synchronized public boolean isConnected() {
        return mConnected;
    }

    public boolean isSqueezeNetwork() {
        return mSqueezeNetwork;
    }

    public long getServerId() {
        return mServerId;
    }

    @Nonnull
    public String getServerHost() {
        if (mServerHost == null) {
            throw new IllegalStateException("server host not set: " + this);
        }
        return mServerHost;
    }

    @Nonnull
    public String getServerName() {
        if (mServerName == null) {
            throw new IllegalStateException("server name not set: " + this);
        }
        return mServerName;
    }

    public int getServerPort() {
        if (mServerPort == 0) {
            throw new IllegalStateException("server port not set: " + this);
        }

        return mServerPort;
    }

    @Nullable
    public String getUsername() {
        return mUsername;
    }

    @Nullable
    public String getPassword() {
        return mPassword;
    }

    /**
     * remember to leave password out of this setting
     */
    @Override
    @Nonnull
    public String toString() {
        // @formatter:off
        return MoreObjects.toStringHelper(this).
                add("serverName", mServerName).
                add("serverId", mServerId).
                add("serverHost", mServerHost).
                add("serverPort", mServerPort).
                add("isSqueezeNetwork", mSqueezeNetwork).
                add("usernameSupplied", mUsername != null).
                add("passwordSupplied", mPassword != null).
                add("connected", isConnected()).
                add("wakeOnLanSettings", mWolSettings).toString();
        // @formatter:on
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mPassword, mServerHost, mServerId, mServerName, mServerPort, mSqueezeNetwork, mUsername, mWolSettings);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ConnectionInfo other = (ConnectionInfo) obj;
        // @formatter:off
        return Objects.equal(mPassword, other.mPassword)
                && Objects.equal(mServerHost, other.mServerHost)
                && Objects.equal(mServerId, other.mServerId)
                && Objects.equal(mServerName, other.mServerName)
                && Objects.equal(mServerPort, other.mServerPort)
                && Objects.equal(mSqueezeNetwork, other.mSqueezeNetwork)
                && Objects.equal(mUsername, other.mUsername)
                && Objects.equal(isConnected(), other.isConnected())
                && Objects.equal(mWolSettings, other.mWolSettings);
        // @formatter:on
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mServerId);
        dest.writeString(mServerHost);
        dest.writeInt(mServerPort);
        dest.writeString(mServerName);
        dest.writeString(mUsername);
        dest.writeString(mPassword);
        dest.writeInt(isConnected() ? 1 : 0);
        dest.writeString(mWolSettings.toJson());
    }

    public static final Parcelable.Creator<ConnectionInfo> CREATOR = new Parcelable.Creator<ConnectionInfo>() {
        @Override
        public ConnectionInfo createFromParcel(Parcel in) {

            long serverId = in.readLong();
            String serverHost = in.readString();
            int serverPort = in.readInt();
            String serverName = in.readString();
            String username = in.readString();
            String password = in.readString();
            boolean connected = in.readInt() != 0;
            String wolSettings = in.readString();

            return new ConnectionInfo(connected, serverId, serverHost, serverPort, serverName, username, password, WakeOnLanSettings.fromJson(wolSettings));
        }

        @Override
        public ConnectionInfo[] newArray(int size) {
            return new ConnectionInfo[size];
        }
    };
}
