/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.content.Context;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.orangebikelabs.orangesqueeze.app.PendingConnection.PendingState;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerMenus;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.SBRequest.CommitType;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;
import com.orangebikelabs.orangesqueeze.common.ThreadTools;
import com.orangebikelabs.orangesqueeze.common.event.AnyPlayerStatusEvent;
import com.orangebikelabs.orangesqueeze.common.event.AppPreferenceChangeEvent;
import com.orangebikelabs.orangesqueeze.common.event.ConnectionStateChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState;
import com.orangebikelabs.orangesqueeze.common.event.PendingConnectionState;
import com.orangebikelabs.orangesqueeze.common.event.PlayerBrowseMenuChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.PlayerListChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.TriggerMenuLoad;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.database.LookupAutoconnectServer;
import com.orangebikelabs.orangesqueeze.net.SBCredentials;
import com.orangebikelabs.orangesqueeze.net.StreamingConnection;
import com.orangebikelabs.orangesqueeze.players.SqueezePlayerHelper;
import com.squareup.otto.Produce;
import com.squareup.otto.Subscribe;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;

/**
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
public class ContextImpl extends AbsContext {

    /**
     * all connection state information
     */
    final protected ConnectionState mConnectionState;

    /**
     * special lifecycle state manager
     */
    final protected ContextLifecycleState mLifecycleState;

    final private Object mLock = new Object();

    /**
     * list of node ids in order
     */
    @GuardedBy("mLock")
    protected ImmutableList<String> mRootMenuNodes = ImmutableList.of();

    final private ThemeSelectorHelper mThemeSelectorHelper = new ThemeSelectorHelper();

    final protected Context mApplicationContext;

    // ignore provider param, it's there to keep anyone else from instantiating
    public ContextImpl(Context context) {
        mApplicationContext = context.getApplicationContext();
        OSAssert.assertNotNull(mApplicationContext, "application context shouldn't be null");
        OSAssert.assertApplicationContext(mApplicationContext);

        mLifecycleState = new ContextLifecycleState(mApplicationContext, this);

        mConnectionState = new ConnectionState();

        BusProvider.getInstance().register(mReceivers);
        BusProvider.getInstance().register(mProducers);
        BusProvider.getInstance().register(new BroadcastMediaMetadataIntents(context));
    }

    @Override
    @Nonnull
    public Context getApplicationContext() {
        return mApplicationContext;
    }

    @Override
    public boolean abortPendingConnection() {
        boolean retval = false;

        boolean abortedAutoConnect = mConnectionState.tryAbortAutoConnect();
        if (mConnectionState.tryAbortPendingConnection()) {
            retval = true;
        } else if (abortedAutoConnect) {
            // we never actually got a pending connection instance to abort, but notify that autoconnect was aborted
            BusProvider.getInstance().post(new PendingConnectionState(false, null));
        }
        return retval;
    }

    @Override
    public boolean awaitConnection(String where) throws InterruptedException {
        OSAssert.assertNotMainThread();
        OSAssert.assertFalse(ThreadTools.isSingleSchedulingThread(), "cannot execute from the scheduling thread");

        try {
            internalAwaitConnection(where, Constants.CONNECTION_TIMEOUT, Constants.TIME_UNITS);
            return true;
        } catch (TimeoutException e) {
            OSLog.i("timed out waiting for connection", e);
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (mConnectionState.takeDisconnect()) {
            mLifecycleState.stopConnections();

            BusProvider.getInstance().post(new ConnectionStateChangedEvent(ConnectionInfo.newDisconnected()));
        }
    }

    @Override
    public boolean finalizePendingConnection() {

        PendingConnection pendingConnection = mConnectionState.takeFinalizableConnection();
        if (pendingConnection == null) {
            return false;
        }

        PlayerId newPlayerId = null;

        // notify for this connection info
        if (pendingConnection.isConnected()) {
            mLifecycleState.stopConnections();
            mLifecycleState.startConnections(pendingConnection.getSubscriptionConnection());
            pendingConnection.activate();

            BusProvider.getInstance().post(new ConnectionStateChangedEvent(pendingConnection.getConnectedInfo()));

            newPlayerId = pendingConnection.getActualPlayerId();

            setRootBrowseNodes(pendingConnection.getRootMenuNodes());

            if (SqueezePlayerHelper.conditionallyStartService(mApplicationContext)) {

                // if we started SqueezePlayer and we would have connected to
                // the SqueezePlayer player that's missing....
                PlayerId desired = pendingConnection.getDesiredPlayerId();
                if (desired != null && !Objects.equal(desired, newPlayerId)) {
                    // automatically select it when it becomes available
                    setAutoSelectSqueezePlayer(true);
                }
            }

            OSExecutors.getUnboundedPool().execute(new AfterConnectionCompleteRunnable(mApplicationContext, pendingConnection));
        } else {
            // disconnected
            BusProvider.getInstance().post(new ConnectionStateChangedEvent(ConnectionInfo.newDisconnected()));
        }

        mConnectionState.setPlayerById(newPlayerId);

        return true;
    }

    @Override
    @Nullable
    public SBCredentials getConnectionCredentials() {
        return mConnectionState.getCredentials();
    }

    @Override
    @Nonnull
    public ConnectionInfo getConnectionInfo() {
        return mConnectionState.getConnectionInfo();
    }

    @Override
    @Nonnull
    public ServerStatus getServerStatus() {
        return mConnectionState.getServerStatus();
    }

    @Override
    @Nullable
    public PlayerId getPlayerId() {
        return mConnectionState.getCurrentPlayerId();
    }

    @Override
    @Nonnull
    public List<String> getRootBrowseNodes() {
        synchronized (mLock) {
            return ImmutableList.copyOf(mRootMenuNodes);
        }
    }

    @Override
    public void setRootBrowseNodes(List<String> nodes) {
        boolean modified = false;
        synchronized (mLock) {
            if (!mRootMenuNodes.equals(nodes)) {
                mRootMenuNodes = ImmutableList.copyOf(nodes);
                modified = true;
            }
        }

        if (modified) {
            // notify about new root menu lineup
            for (PlayerId pid : getServerStatus().getAvailablePlayerIds()) {
                BusProvider.getInstance().post(new PlayerBrowseMenuChangedEvent(pid));
            }

            PlayerMenuHelper.triggerStoreMenus(0, TimeUnit.SECONDS);
        }
    }

    @Override
    @Nonnull
    public FutureResult incrementPlayerVolume(PlayerId playerId, int volumeDiff) {
        VolumeCommandHelper helper = VolumeCommandHelper.getInstance(playerId);
        return helper.incrementPlayerVolume(volumeDiff);
    }

    /**
     * give access to this to CometRequest
     */
    @Nonnull
    public StreamingConnection internalAwaitConnection(String where, long time, TimeUnit units) throws InterruptedException, TimeoutException {
        // we should never wait on the main thread
        OSAssert.assertNotMainThread();

        StreamingConnection readyConnection = mLifecycleState.awaitConnectionObjectAvailable(where, time, units);
        if (readyConnection != null) {
            if (!readyConnection.awaitConnection(time, units)) {
                // connection not established
                readyConnection = null;
            }
        }
        if (readyConnection == null) {
            throw new TimeoutException();
        }
        return readyConnection;
    }

    @Override
    public boolean isConnecting() {
        return mConnectionState.isConnecting();
    }

    @Override
    @Nonnull
    public SBRequest newRequest(SBRequest.Type requestType, List<?> commands) {
        switch (requestType) {
            case JSONRPC:
                return new JsonRpcRequest(mApplicationContext, this, commands);
            case COMET:
                return new CometRequest(mApplicationContext, this, commands);
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    @Nonnull
    public SBRequest newRequest(List<?> commands) {
        return newRequest(SBRequest.Type.JSONRPC, commands);
    }

    @Override
    public void onStart(Context context) {
        mLifecycleState.onStart(context);
    }

    @Override
    public void onStop(Context context) {
        mLifecycleState.onStop(context);
    }

    @Override
    public void temporaryOnStart(Context context, long time, TimeUnit units) {
        mLifecycleState.onServiceCreate(context);
        AndroidSchedulers.mainThread().scheduleDirect(() -> {
            mLifecycleState.onServiceDestroy(context);
        }, time, units);
    }

    @Override
    public void onServiceCreate(Context context) {
        mLifecycleState.onServiceCreate(context);
    }

    @Override
    public void onServiceDestroy(Context context) {
        mLifecycleState.onServiceDestroy(context);
    }

    @Override
    @Nonnull
    public FutureResult renamePlayer(PlayerId playerId, String newName) {
        return sendPlayerCommand(playerId, "name", newName);
    }

    @Override
    @Nonnull
    public FutureResult sendPlayerCommand(@Nullable PlayerId playerId, List<?> commands) {
        AbsRequest request = (AbsRequest) newRequest(SBRequest.Type.JSONRPC, commands);
        // null playerId means use current player
        if (playerId == null) {
            playerId = getPlayerId();

            // still null, no current player
            if (playerId == null) {
                return FutureResult.immediateFailedResult(new SBRequestException("no player selected"));
            }
        }

        request.setPlayerId(playerId);
        request.setCommitType(CommitType.PLAYERUPDATE);
        return request.submit(OSExecutors.getCommandExecutor());
    }

    @Override
    public void setAutoSelectSqueezePlayer(boolean autoSelect) {
        mConnectionState.setAutoSelectSqueezePlayer(autoSelect);
    }

    @Override
    public void setConnectionCredentials(@Nullable SBCredentials credentials) {
        mConnectionState.setCredentials(credentials);
    }

    @Override
    public boolean setPlayerById(@Nullable PlayerId newPlayerId) {
        boolean retval = false;
        if (mConnectionState.setPlayerById(newPlayerId)) {
            if (newPlayerId != null) {
                // player changed, update the last player selected column in the database
                final long serverId = mConnectionState.getConnectionInfo().getServerId();
                OSExecutors.getUnboundedPool().execute(new AfterPlayerChangedRunnable(mApplicationContext, serverId, newPlayerId));
            }
            retval = true;
        }
        return retval;
    }

    @Override
    @Nonnull
    public FutureResult setPlayerVolume(PlayerId playerId, int volume) {
        VolumeCommandHelper helper = VolumeCommandHelper.getInstance(playerId);
        return helper.setPlayerVolume(volume);
    }

    @Override
    public void startAutoConnect() {
        // does user want to auto-connect?
        if (!SBPreferences.get().isAutoConnectEnabled()) {
            return;
        }

        if (!mConnectionState.takeAutoConnect()) {
            return;
        }

        OSExecutors.getUnboundedPool().execute(() -> {
            // find server on which autoConnect is enabled
            List<LookupAutoconnectServer> servers = DatabaseAccess.getInstance(mApplicationContext).getServerQueries().lookupAutoconnectServer().executeAsList();
            if (!servers.isEmpty()) {
                LookupAutoconnectServer server = servers.get(0);
                startPendingConnection(server.get_id(), server.getServername(), true);
            } else {
                abortPendingConnection();
            }
        });

    }

    @Override
    public void startPendingConnection(long serverId, String serverDisplayName) {
        startPendingConnection(serverId, serverDisplayName, false);
    }

    public void startPendingConnection(long serverId, String serverDisplayName, final boolean autoFinalizeConnection) {
        // this is often run on main thread, don't block
        PendingConnection newPendingConnection = new PendingConnection(this, serverId, serverDisplayName);

        mConnectionState.setPendingConnection(newPendingConnection);

        ListenableFuture<PendingState> connectionFuture = newPendingConnection.submit(OSExecutors.getUnboundedPool());

        // ensure this callback runs on a background thread, if pending connection completes/succeeds very quickly this could run on samethread otherwise
        Futures.addCallback(connectionFuture, new FutureCallback<>() {
            @Override
            public void onFailure(@Nullable Throwable e) {
                if (e != null) {
                    OSLog.w(e.getMessage(), e);
                }
            }

            @Override
            public void onSuccess(@Nullable PendingState state) {
                if (state == PendingState.SUCCESS && autoFinalizeConnection) {
                    finalizePendingConnection();
                }
            }
        }, OSExecutors.getUnboundedPool());
    }

    @Override
    @Nonnull
    public FutureResult synchronize(PlayerId playerId, PlayerId targetPlayerId) {
        FutureResult retval = sendPlayerCommand(targetPlayerId, "sync", playerId.toString());

        // update local sync status immediately
        if (getServerStatus().getSyncStatus().synchronize(playerId, targetPlayerId)) {
            BusProvider.getInstance().post(new PlayerListChangedEvent(getServerStatus()));
        }

        return retval;
    }

    @Override
    @Nonnull
    public FutureResult unsynchronize(PlayerId playerId) {
        FutureResult retval = sendPlayerCommand(playerId, "sync", "-");

        // update local sync status immediately
        if (getServerStatus().getSyncStatus().unsynchronize(playerId)) {
            BusProvider.getInstance().post(new PlayerListChangedEvent(getServerStatus()));
        }

        return retval;
    }

    /* begin event receiver block */
    final private Object mReceivers = new Object() {

        @Subscribe
        public void whenAppPreferenceChanged(AppPreferenceChangeEvent event) {
            if (event.getKey().equals(SBPreferences.getAutoDiscoveryKey(mApplicationContext))) {
                // reset the discovery service, in background
                OSExecutors.getUnboundedPool().execute(mLifecycleState::restartDiscoveryService);
            } else if (SBPreferences.get().isCompactModePreference(event.getKey())) {
                mThemeSelectorHelper.setCompactMode(SBPreferences.get().isCompactMode());
            }
        }

        @Subscribe
        public void whenAnyPlayerChange(AnyPlayerStatusEvent event) {
            // convert an AnyPlayerStatusEvent into a current player change event
            PlayerId eventId = event.getPlayerStatus().getId();
            PlayerId currentPlayerId = getPlayerId();
            boolean currentPlayer = eventId.equals(currentPlayerId);
            if (currentPlayer) {
                BusProvider.getInstance().post(new CurrentPlayerState(event.getPlayerStatus(), event.getPreviousPlayerStatus()));
            }
        }

        @Subscribe
        public void whenPlayerListChanges(PlayerListChangedEvent event) {
            boolean playerSet;

            playerSet = checkAutoSelectSqueezePlayer();

            if (!playerSet) {
                Set<PlayerId> playerList = getServerStatus().getAvailablePlayerIds();
                if (playerList.isEmpty()) {
                    setPlayerById(null);
                } else {
                    PlayerId currentPlayerId = getPlayerId();
                    if (currentPlayerId == null) {

                        // were there any players removed?
                        PlayerId candidate = null;
                        // set to the first powered player
                        for (PlayerStatus s : getServerStatus().getAvailablePlayers()) {
                            // set candidate to the first powered player
                            if (s.isPowered()) {
                                candidate = s.getId();
                                break;
                            }
                        }
                        if (candidate == null) {
                            // finally, set to a random player
                            candidate = playerList.iterator().next();
                        }
                        setPlayerById(candidate);
                    }
                }
            }
        }

        @Subscribe
        public void whenTriggerMenuReload(TriggerMenuLoad event) {
            for (PlayerId id : getServerStatus().getAvailablePlayerIds()) {
                PlayerMenus menus = getPlayerMenus(id);

                // force refresh
                menus.triggerReload();
            }
        }
    };

    /* begin producer blocks for Otto EventBus */
    final private Object mProducers = new Object() {
        @Produce
        public CurrentPlayerState produceCurrentPlayerStatusEvent() {
            return new CurrentPlayerState(getPlayerStatus(), null);
        }
    };

    /**
     * check whether we should automatically selected the inbound squeezeplayer instance, and if so, do it
     *
     * @return true if an instance was found and selected
     */
    protected boolean checkAutoSelectSqueezePlayer() {
        boolean retval = false;
        // if we just launched SqueezePlayer, set the new player as the
        // active player immediately
        for (PlayerStatus s : getServerStatus().getAvailablePlayers()) {
            if (s.isLocalSqueezePlayer() && mConnectionState.takeAutoSelectSqueezePlayerFlag()) {
                retval = true;
                setPlayerById(s.getId());
                break;
            }
        }
        return retval;
    }
}
