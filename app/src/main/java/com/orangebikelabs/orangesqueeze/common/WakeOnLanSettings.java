/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.compat.Compat;
import com.orangebikelabs.orangesqueeze.net.NetworkTools;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * This structure holds our server-specific wake on lan settings. It's designed to be serialized to JSON and stored in the database.
 *
 * @author tbsandee@orangebikelabs.com
 */
@Keep
@ThreadSafe
public class WakeOnLanSettings implements Serializable {

    /**
     * our pattern for JSON parsing, if an error occurs just reset to the default settings gracefully
     */
    @Nonnull
    static public WakeOnLanSettings fromJson(@Nullable String data) {
        WakeOnLanSettings retval = null;
        if (data != null) {
            try {
                retval = JsonHelper.getJsonObjectReader().forType(WakeOnLanSettings.class).readValue(data);
            } catch (IOException e) {
                Reporting.report(e, "Error deserializing Wake On Lan settings", data);
            }
        }
        if (retval == null) {
            retval = new WakeOnLanSettings();
        }
        return retval;
    }

    /**
     * the various wake on lan modes: never, on connection
     */
    @Keep
    public enum Mode {
        NEVER, CONNECTION
    }

    @GuardedBy("this")
    @Nullable
    private String mMacAddress;

    @GuardedBy("this")
    private boolean mAutodetectMacAddress = Compat.isArpLookupAllowed();

    @GuardedBy("this")
    @Nonnull
    private Mode mMode = Mode.CONNECTION;

    @GuardedBy("this")
    @Nonnull
    private List<Integer> mPorts;

    @GuardedBy("this")
    @Nullable
    private String mBroadcastAddress;

    public WakeOnLanSettings() {
        // set up the default ports
        mPorts = new ArrayList<>(NetworkTools.DEFAULT_WAKEONLAN_PORTS);
    }

    @Nullable
    synchronized public String getMacAddress() {
        return mMacAddress;
    }

    synchronized public void setMacAddress(@Nullable String macAddress) {
        mMacAddress = macAddress;
    }

    synchronized public boolean getAutodetectMacAddress() {
        return mAutodetectMacAddress;
    }

    synchronized public void setAutodetectMacAddress(boolean allowAutodetect) {
        mAutodetectMacAddress = allowAutodetect;
    }

    @Nonnull
    synchronized public Mode getMode() {
        return mMode;
    }

    synchronized public void setMode(Mode mode) {
        mMode = mode;
    }

    @Nonnull
    synchronized public List<Integer> getPorts() {
        return mPorts;
    }

    synchronized public void setPorts(List<Integer> ports) {
        mPorts = ports;
    }

    @Nullable
    synchronized public String getBroadcastAddress() {
        return mBroadcastAddress;
    }

    synchronized public void setBroadcastAddress(@Nullable String address) {
        mBroadcastAddress = address;
    }

    @JsonIgnore
    synchronized public boolean wakeDuringConnection() {
        return mMode == Mode.CONNECTION;
    }

    @Nonnull
    synchronized public String toJson() {
        try {
            return JsonHelper.getJsonObjectWriter().writeValueAsString(this);
        } catch (IOException e) {
            Reporting.report(e, "Error serializing Wake On Lan settings", this);
            return "";
        }
    }

    @Override
    @Nonnull
    synchronized public String toString() {
        // @formatter:off
        return MoreObjects.toStringHelper(this).
                add("macAddress", mMacAddress).
                add("autodetectMacAddress", mAutodetectMacAddress).
                add("broadcast", mBroadcastAddress).
                add("mode", mMode).
                add("ports", mPorts).
                toString();
        // @formatter:on
    }
}
