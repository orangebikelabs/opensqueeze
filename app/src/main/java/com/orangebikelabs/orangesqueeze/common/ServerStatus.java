/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.orangebikelabs.orangesqueeze.common.event.AnyPlayerStatusEvent;
import com.orangebikelabs.orangesqueeze.common.event.CurrentServerState;
import com.orangebikelabs.orangesqueeze.common.event.PlayerListChangedEvent;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
@ThreadSafe
public class ServerStatus {
    final static protected ThreadLocal<Transaction> sCurrentTransaction = new ThreadLocal<>();

    /**
     * this is a shared transaction lock used to ensure only one status update transaction is open at a time
     */
    final protected ReentrantLock mTransactionLock = new ReentrantLock();

    @NotThreadSafe
    public class Transaction implements Closeable {
        final private List<PlayerStatus> mUpdateList = new ArrayList<>(1);
        final private List<PlayerMenus> mMenuUpdates = new ArrayList<>(1);
        final private List<List<PlayerId>> mSyncUpdates = new ArrayList<>();

        private boolean mSuccess;

        @SuppressWarnings("LockAcquiredButNotSafelyReleased")
        protected Transaction() {
            mTransactionLock.lock();
            sCurrentTransaction.set(this);
        }

        public void add(PlayerStatus status) {
            mUpdateList.add(status);
        }

        public void add(PlayerMenus menus) {
            mMenuUpdates.add(menus);
        }

        public void markSuccess() {
            mSuccess = true;
        }

        public void setSyncGroup(PlayerId master, PlayerId[] slaves) {
            mSyncUpdates.add(Lists.asList(master, slaves));
        }

        public void setNoSync(PlayerId id) {
            mSyncUpdates.add(Collections.singletonList(id));
        }

        @Override
        public void close() {
            try {
                if (mSuccess) {
                    addPlayerStatusList(mUpdateList);
                    mPlayerMenusContainer.commitUpdates(mMenuUpdates);
                    updateSyncStatus(mSyncUpdates);
                }
            } finally {
                mTransactionLock.unlock();
                sCurrentTransaction.set(null);
            }
        }
    }

    @GuardedBy("this")
    @Nullable
    protected Long mLastScanTime;

    @GuardedBy("this")
    @Nonnull
    protected VersionIdentifier mServerVersion = new VersionIdentifier("0");

    /**
     * stores player info, key=id, value=PlayerStatus
     */
    @GuardedBy("this")
    @Nonnull
    final protected Map<PlayerId, PlayerStatus> mPlayers = new HashMap<>();

    @GuardedBy("this")
    @Nonnull
    protected SortedSet<OtherPlayerInfo> mOtherPlayers = ImmutableSortedSet.of();

    /**
     * sync status
     */
    @Nonnull
    final private SyncStatus mSyncStatus = new SyncStatus();

    @Nonnull
    final private PlayerMenusContainer mPlayerMenusContainer = new PlayerMenusContainer();

    /**
     * create initial serverstatus
     */
    public ServerStatus() {
    }

    @Nonnull
    public PlayerMenus getPlayerMenus(PlayerId playerId) {
        return mPlayerMenusContainer.getInstance(playerId);
    }

    /**
     * returns list of AVAILABLE (both connected and disconnected) players
     */
    @Nonnull
    public ImmutableList<PlayerStatus> getAvailablePlayers() {
        List<PlayerStatus> retval;

        synchronized (this) {
            // return set sorted by player name, not hidden ID
            retval = new ArrayList<>(mPlayers.values());
        }
        Collections.sort(retval, PlayerStatus.newDefaultComparator());
        return ImmutableList.copyOf(retval);
    }

    /**
     * returns set of AVAILABLE (both connected and disconnected) player IDs
     */
    @Nonnull
    public ImmutableSet<PlayerId> getAvailablePlayerIds() {
        ImmutableSet.Builder<PlayerId> builder = ImmutableSet.builder();
        for (PlayerStatus s : getAvailablePlayers()) {
            builder.add(s.getId());
        }
        return builder.build();
    }

    @Nonnull
    synchronized public SortedSet<OtherPlayerInfo> getOtherPlayers() {
        return mOtherPlayers;
    }

    @Nullable
    synchronized public PlayerStatus getPlayerStatus(PlayerId id) {
        OSAssert.assertNotNull(id, "non-null player id expected");
        return mPlayers.get(id);
    }

    @Nonnull
    public PlayerStatus getCheckedPlayerStatus(PlayerId id) throws PlayerNotFoundException {
        PlayerStatus status = getPlayerStatus(id);
        if (status == null) {
            throw new PlayerNotFoundException(id);
        }
        return status;
    }

    @Nullable
    synchronized public Long getLastScanTime() {
        return mLastScanTime;
    }

    synchronized public boolean isConnectedPlayerId(PlayerId playerId) {
        PlayerStatus status = mPlayers.get(playerId);
        if (status == null) {
            return false;
        }
        return status.isConnected();
    }

    @Nonnull
    synchronized public VersionIdentifier getVersion() {
        return mServerVersion;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("lastScanTime", getLastScanTime()).
                add("serverVersion", getVersion()).
                add("players", getAvailablePlayers()).
                add("otherPlayers", getOtherPlayers()).
                add("syncStatus", getSyncStatus()).
                toString();
    }

    @Nonnull
    public Transaction newTransaction() {
        return new Transaction();
    }

    @Nonnull
    public SyncStatus getSyncStatus() {
        return mSyncStatus;
    }

    public void set(JsonNode o, List<PlayerId> currentPlayerList, List<PlayerId> removedPlayerList) {

        List<PlayerStatus> oldAvailablePlayers = getAvailablePlayers();
        Set<PlayerId> oldAvailablePlayerIds = getAvailablePlayerIds();

        ServerStatusUpdateProcessor processor = new ServerStatusUpdateProcessor(oldAvailablePlayers);
        processor.run(o);

        currentPlayerList.clear();
        currentPlayerList.addAll(processor.getNewPlayerIds());

        // keep locked block as short as possible
        synchronized (this) {
            mServerVersion = new VersionIdentifier(o.path("version").asText());

            // add any players we're missing
            addPlayerStatusList(processor.mPlayersToAdd);

            // get list of available players
            Set<PlayerId> availablePlayers = new HashSet<>(mPlayers.keySet());
            Set<PlayerId> notFoundPlayers = Sets.symmetricDifference(availablePlayers, processor.mFoundPlayers);

            // remove each player from the player map that is not found
            for (PlayerId removePlayer : notFoundPlayers) {
                mPlayers.remove(removePlayer);
            }
            removedPlayerList.addAll(notFoundPlayers);

            mOtherPlayers = ImmutableSortedSet.copyOf(processor.mOtherPlayers);

            // this element is missing while scanning
            JsonNode node = o.get("lastscan");
            if (node == null) {
                mLastScanTime = null;
            } else {
                mLastScanTime = node.asLong();
            }
        }

        if (!oldAvailablePlayerIds.equals(processor.getNewPlayerIds())) {
            mPlayerMenusContainer.setPlayerList(processor.getNewPlayerIds());
            BusProvider.getInstance().post(new PlayerListChangedEvent(this));
        }

        BusProvider.getInstance().post(new CurrentServerState(this));
    }

    protected void updateSyncStatus(List<List<PlayerId>> syncUpdates) {
        if (mSyncStatus.updateSyncStatus(syncUpdates)) {
            // only issue notification if the sync group structure changed
            BusProvider.getInstance().post(new PlayerListChangedEvent(this));
        }
    }

    synchronized protected void addPlayerStatusList(List<PlayerStatus> list) {
        for (PlayerStatus ps : list) {
            PlayerStatus old = mPlayers.put(ps.getId(), ps);
            if (ps != old) {
                // only issue notification is the playerstatus changed
                BusProvider.getInstance().post(new AnyPlayerStatusEvent(ps, old));
            }
        }
    }

    static class ServerStatusUpdateProcessor {
        @Nonnull
        final private List<PlayerStatus> mCurrentPlayers;

        @Nonnull
        final private List<OtherPlayerInfo> mOtherPlayers = new ArrayList<>();

        @Nonnull
        final private Set<PlayerId> mFoundPlayers = new LinkedHashSet<>();

        @Nonnull
        final private List<PlayerStatus> mPlayersToAdd = new ArrayList<>();

        @Nonnull
        final private Set<PlayerId> mNewPlayerIds = new LinkedHashSet<>();

        public ServerStatusUpdateProcessor(List<PlayerStatus> currentPlayers) {
            mCurrentPlayers = currentPlayers;
        }

        @Nonnull
        public Set<PlayerId> getNewPlayerIds() {
            return mNewPlayerIds;
        }

        public void run(JsonNode o) {
            OSLog.jsonTrace("New server status", o);

            processOtherPlayers(o);
            processSqueezeNetworkPlayers(o);

            // parse player loop, changes to the players happens immediately but player additions/removals are added at the end of the block
            JsonNode players = o.get("players_loop");
            if (players != null && players.isArray()) {
                for (int i = 0; i < players.size(); i++) {
                    JsonNode player = players.get(i);

                    JsonNode playerIdNode = player.get("playerid");
                    if (playerIdNode == null) {
                        continue;
                    }

                    PlayerId playerId = new PlayerId(playerIdNode.asText());

                    mFoundPlayers.add(playerId);
                    PlayerStatus status = getCurrentPlayerStatus(playerId);
                    if (status == null) {
                        status = new PlayerStatus(playerId);
                    }
                    status = status.withServerStatusUpdate(player);

                    mNewPlayerIds.add(status.getId());
                    mPlayersToAdd.add(status);
                }
            }
        }

        @Nullable
        private PlayerStatus getCurrentPlayerStatus(PlayerId id) {
            for (int i = 0; i < mCurrentPlayers.size(); i++) {
                if (mCurrentPlayers.get(i).getId().equals(id)) {
                    return mCurrentPlayers.get(i);
                }
            }
            return null;
        }

        private void processSqueezeNetworkPlayers(JsonNode o) {
            JsonNode snPlayersNode = o.get("sn_players_loop");
            try {
                if (snPlayersNode != null) {
                    ObjectReader or = JsonHelper.getJsonObjectReader().forType(new TypeReference<List<OtherPlayerInfo>>() {/* no overrides */
                    });
                    List<OtherPlayerInfo> playerList = or.readValue(snPlayersNode.traverse());

                    // ensure they are marked as being from SqueezeNetwork
                    for (OtherPlayerInfo opi : playerList) {
                        opi.markSqueezeNetwork();
                    }

                    mOtherPlayers.addAll(playerList);
                }
            } catch (IOException e) {
                Reporting.report(e, "Error deserializing SN players node", snPlayersNode);
            }
        }

        private void processOtherPlayers(JsonNode o) {
            // In general, do parsing outside of lock
            JsonNode otherPlayersNode = o.get("other_players_loop");
            try {
                if (otherPlayersNode != null) {
                    ObjectReader or = JsonHelper.getJsonObjectReader().forType(new TypeReference<List<OtherPlayerInfo>>() {/* no overrides */
                    });
                    List<OtherPlayerInfo> playerList = or.readValue(otherPlayersNode.traverse());
                    mOtherPlayers.addAll(playerList);
                }
            } catch (IOException e) {
                Reporting.report(e, "Error deserializing other players node", otherPlayersNode);
            }
        }
    }
}
