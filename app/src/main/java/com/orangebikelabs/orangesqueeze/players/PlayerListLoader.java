/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.content.Context;
import androidx.loader.content.AsyncTaskLoader;

import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.OtherPlayerInfo;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;
import com.orangebikelabs.orangesqueeze.common.SyncStatus;
import com.orangebikelabs.orangesqueeze.common.event.ActivePlayerChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.AnyPlayerStatusEvent;
import com.orangebikelabs.orangesqueeze.common.event.CurrentServerState;
import com.orangebikelabs.orangesqueeze.common.event.PlayerListChangedEvent;
import com.orangebikelabs.orangesqueeze.players.PlayerListLoader.PlayerListContainer;
import com.squareup.otto.Subscribe;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Class is final to register with BusProvider.
 *
 * @author tbsandee@orangebikelabs.com
 */
final public class PlayerListLoader extends AsyncTaskLoader<PlayerListContainer> {
    static public class PlayerListContainer {
        protected PlayerListContainer(Map<PlayerId, PlayerStatus> connectedPlayers, SortedSet<OtherPlayerInfo> otherPlayers,
                                      @Nullable PlayerId currentPlayerId, SyncStatus playerSyncState, boolean squeezePlayerFound, boolean squeezePlayerAvailable) {
            mConnectedPlayers = connectedPlayers;
            mCurrentPlayerId = currentPlayerId;
            mSqueezePlayerFound = squeezePlayerFound;
            mSqueezePlayerAvailable = squeezePlayerAvailable;
            mOtherPlayers = otherPlayers;
            mPlayerSyncState = playerSyncState;
        }

        final public Map<PlayerId, PlayerStatus> mConnectedPlayers;
        final public SortedSet<OtherPlayerInfo> mOtherPlayers;

        @Nullable
        final public PlayerId mCurrentPlayerId;
        final public boolean mSqueezePlayerFound;
        final public boolean mSqueezePlayerAvailable;
        final public SyncStatus mPlayerSyncState;
    }

    final private ForceLoadContentObserver mObserver = new ForceLoadContentObserver();
    private boolean mRegistered = false;

    public PlayerListLoader(Context context) {
        super(context);

        // there's data right away, start loading when it's time
        mObserver.onChange(true);

        // 1/2 second throttling
        setUpdateThrottle(500);
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();

        unregisterBusListener();
    }

    @Override
    protected void onReset() {
        super.onReset();

        unregisterBusListener();
    }

    /**
     * Runs on the UI thread
     */
    @Override
    public void deliverResult(@Nullable PlayerListContainer requestData) {
        if (isStarted()) {
            super.deliverResult(requestData);
        }
    }

    private void registerBusListener() {
        if (!mRegistered) {
            mRegistered = true;
            BusProvider.getInstance().register(mEventReceiver);
        }
    }

    private void unregisterBusListener() {
        if (mRegistered) {
            mRegistered = false;
            BusProvider.getInstance().unregister(mEventReceiver);
        }
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        registerBusListener();

        takeContentChanged();

        // always reload onStart
        forceLoad();
    }

    @Override
    @Nonnull
    public PlayerListContainer loadInBackground() {
        SBContext sbContext = SBContextProvider.get();
        ServerStatus serverStatus = sbContext.getServerStatus();

        boolean squeezePlayerFound = false;
        Map<PlayerId, PlayerStatus> players = new HashMap<>();
        for (PlayerStatus s : serverStatus.getAvailablePlayers()) {
            players.put(s.getId(), s);
            if (s.isLocalSqueezePlayer()) {
                squeezePlayerFound = true;
            }
        }

        boolean squeezePlayerAvailable = false;

        if (SqueezePlayerHelper.isAvailable(sbContext.getApplicationContext())) {
            squeezePlayerAvailable = true;
        }
        return new PlayerListContainer(players, serverStatus.getOtherPlayers(), sbContext.getPlayerId(), serverStatus.getSyncStatus().getReadonlySnapshot(),
                squeezePlayerFound, squeezePlayerAvailable);
    }

    private void notifyChange() {
        mObserver.dispatchChange(false, null);
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenPlayerListChanges(PlayerListChangedEvent event) {
            notifyChange();
        }

        @Subscribe
        public void whenActivePlayerChanges(ActivePlayerChangedEvent event) {
            notifyChange();
        }

        @Subscribe
        public void whenAnyPlayerStatusChanges(AnyPlayerStatusEvent event) {
            notifyChange();
        }

        @Subscribe
        public void whenServerStatusChanges(CurrentServerState event) {
            notifyChange();
        }
    };

}
