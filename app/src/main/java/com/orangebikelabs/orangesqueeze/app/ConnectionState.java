/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.os.SystemClock;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;
import com.orangebikelabs.orangesqueeze.common.event.ActivePlayerChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState;
import com.orangebikelabs.orangesqueeze.common.event.CurrentServerState;
import com.orangebikelabs.orangesqueeze.common.event.PendingConnectionState;
import com.orangebikelabs.orangesqueeze.net.SBCredentials;
import com.squareup.otto.Produce;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * This class houses all mutable connection information. It's completely threadsafe and minimizes locking/blocking to prevent deadlocks,
 * given that requests can come in from many threads.
 *
 * @author tsandee
 */
@ThreadSafe
public class ConnectionState {
    @GuardedBy("this")
    @Nonnull
    private ConnectionInfo mConnectionInfo = ConnectionInfo.newDisconnected();

    @GuardedBy("this")
    @Nonnull
    private ServerStatus mServerStatus;

    @GuardedBy("this")
    @Nullable
    private PlayerId mCurrentPlayerId;

    @GuardedBy("this")
    private boolean mAutoConnecting;

    @GuardedBy("this")
    @Nullable
    private PendingConnection mPendingConnection;

    @GuardedBy("this")
    @Nullable
    private SBCredentials mCredentials;

    @GuardedBy("this")
    private long mAutoSelectSqueezePlayerTime;

    public ConnectionState() {
        // set up empty server status
        mServerStatus = new ServerStatus();

        BusProvider.getInstance().register(mProducers);
    }

    @Nonnull
    synchronized public ConnectionInfo getConnectionInfo() {
        return mConnectionInfo;
    }

    @Nonnull
    synchronized public ServerStatus getServerStatus() {
        return mServerStatus;
    }

    @Nullable
    synchronized public PlayerId getCurrentPlayerId() {
        return mCurrentPlayerId;
    }

    synchronized public boolean isConnecting() {
        // if we are currently trying to connect, return true
        return (mPendingConnection != null && mPendingConnection.isConnecting()) || mAutoConnecting;
    }

    /**
     * @return true if auto connect must be triggered
     */
    synchronized boolean takeAutoConnect() {
        // are we connected or is there a pending connection already?
        if (mAutoConnecting || mConnectionInfo.isConnected() || mPendingConnection != null) {
            return false;
        }

        mAutoConnecting = true;
        return true;
    }

    boolean tryAbortAutoConnect() {
        boolean retval;
        synchronized (this) {
            retval = mAutoConnecting;
            mAutoConnecting = false;
        }
        return retval;
    }

    boolean tryAbortPendingConnection() {
        boolean retval = false;
        PendingConnection abortablePendingConnection;

        // keep synchronized block outside of call to abort connection to avoid potential for deadlock
        synchronized (this) {
            abortablePendingConnection = mPendingConnection;
            mPendingConnection = null;
        }

        if (abortablePendingConnection != null) {
            // this could block for a brief moment due to synchronization
            abortablePendingConnection.abort();
            retval = true;
        }
        return retval;
    }

    void setPendingConnection(PendingConnection newPendingConnection) {
        tryAbortPendingConnection();

        synchronized (this) {
            mAutoConnecting = false;
            mPendingConnection = newPendingConnection;
        }
    }

    /**
     * if a pending connection is ready to be connected, take it in an atomic operation
     */
    @Nullable
    synchronized public PendingConnection takeFinalizableConnection() {
        PendingConnection retval = mPendingConnection;
        if (retval != null) {
            if (!retval.isConnected()) {
                // do nothing if we're not already connected
                retval = null;
            } else {
                mConnectionInfo = retval.getConnectedInfo();
                mServerStatus = retval.getServerStatus();

                // it's no longer pending...
                mPendingConnection = null;
            }
        }
        return retval;
    }

    synchronized public boolean takeDisconnect() {
        boolean retval = false;

        if (mConnectionInfo.isConnected()) {
            mConnectionInfo.setConnected(false);
            // empty server status
            mServerStatus = new ServerStatus();
            mAutoConnecting = false;
            mCurrentPlayerId = null;
            mPendingConnection = null;
            mAutoSelectSqueezePlayerTime = 0L;
            retval = true;
        }
        return retval;
    }

    synchronized public void setAutoSelectSqueezePlayer(boolean autoSelect) {
        if (autoSelect) {
            // if squeezeplayer shows up in the next 10 seconds or so autoselect it
            mAutoSelectSqueezePlayerTime = SystemClock.uptimeMillis() + 10000;
        } else {
            mAutoSelectSqueezePlayerTime = 0L;
        }
    }

    synchronized public boolean takeAutoSelectSqueezePlayerFlag() {
        boolean retval = false;
        if (mAutoSelectSqueezePlayerTime != 0 && SystemClock.uptimeMillis() < mAutoSelectSqueezePlayerTime) {
            mAutoSelectSqueezePlayerTime = 0L;
            retval = true;
        }
        return retval;
    }

    /**
     * returns true if the current player changed
     */
    synchronized public boolean setPlayerById(@Nullable PlayerId newPlayerId) {
        boolean retval = false;

        PlayerId oldPlayerId = mCurrentPlayerId;

        if (!Objects.equal(newPlayerId, oldPlayerId)) {
            Optional<PlayerStatus> newPlayerStatus = null;
            if (newPlayerId == null) {
                newPlayerStatus = Optional.absent();
            } else {
                PlayerStatus temp = mServerStatus.getPlayerStatus(newPlayerId);
                if (temp == null) {
                    // invalid player id, just log a message
                    OSLog.i("Invalid player ID " + newPlayerId);
                } else {
                    newPlayerStatus = Optional.of(temp);
                }
            }
            if (newPlayerStatus != null) {
                retval = true;
                // update player id
                mCurrentPlayerId = newPlayerId;
                if (newPlayerStatus.isPresent() && newPlayerStatus.get().isLocalSqueezePlayer()) {
                    SBPreferences.get().setLastConnectedSqueezePlayerId(newPlayerId);
                }

                // post event
                BusProvider.getInstance().post(new ActivePlayerChangedEvent(newPlayerStatus.orNull()));

                // also, this event fires any time the current player status is updated, which by definition occurs when the player changes
                BusProvider.getInstance().post(new CurrentPlayerState(newPlayerStatus.orNull(), null));
            }
        }
        return retval;
    }

    @Nullable
    synchronized public SBCredentials getCredentials() {
        return mCredentials;
    }

    synchronized public void setCredentials(@Nullable SBCredentials credentials) {
        mCredentials = credentials;
    }

    final private Object mProducers = new Object() {
        @Produce
        synchronized public CurrentServerState produceCurrentServerStatusEvent() {
            return new CurrentServerState(mServerStatus);
        }

        @Produce
        synchronized public PendingConnectionState producePendingConnection() {
            return new PendingConnectionState(isConnecting(), mPendingConnection);
        }
    };
}
