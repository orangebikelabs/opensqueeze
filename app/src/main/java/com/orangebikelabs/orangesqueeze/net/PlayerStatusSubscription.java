/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.app.SimpleResult;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerNotFoundException;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;
import com.orangebikelabs.orangesqueeze.common.ServerStatus.Transaction;
import com.orangebikelabs.orangesqueeze.common.TrackInfo;
import com.orangebikelabs.orangesqueeze.net.StreamingConnection.Subscription;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class PlayerStatusSubscription extends Subscription {

    static final private ListMultimap<PlayerId, SimpleResult> sPlayerCommittableResults =
            Multimaps.synchronizedListMultimap(ArrayListMultimap.create());

    static public void registerCommittableResult(PlayerId playerId, SimpleResult result) {
        sPlayerCommittableResults.put(playerId, result);
    }

    final private PlayerId mPlayerId;

    public PlayerStatusSubscription(StreamingConnection connection, PlayerId playerId) {
        super(connection);
        mPlayerId = playerId;
    }

    @Override
    public void onSuccess(@Nullable JsonNode o) {
        OSAssert.assertNotNull(o, "can't be null");

        SBContext sbContext = mConnection.getSbContext();
        Transaction transaction = sbContext.getServerStatus().newTransaction();
        try {
            PlayerStatus playerStatus = sbContext.getServerStatus().getCheckedPlayerStatus(mPlayerId);

            // assuming 1/4 second latency has worked well so far
            final long timestampDelta = -250L;

            JsonNode playerData = o.path("data");
            PlayerStatus newStatus = playerStatus.withPlayerStatusUpdate(mConnection.getApplicationContext(), sbContext.getServerId(), playerData, timestampDelta);
            transaction.add(newStatus);

            if (newStatus.needsTrackLookup()) {
                String trackId = newStatus.getTrackId().orNull();
                if (trackId != null) {
                    // if server connection isn't fully activated, defer the track lookup so that cache can function
                    if (SBContextProvider.get().getServerId() == ConnectionInfo.INVALID_SERVER_ID) {
                        OSExecutors.getSingleThreadScheduledExecutor()
                                .schedule(() -> startTrackLookup(sbContext, newStatus.getId(), trackId),
                                        2, TimeUnit.SECONDS);
                    } else {
                        startTrackLookup(sbContext, newStatus.getId(), trackId);
                    }
                }
            }

            JsonNode syncMaster;
            syncMaster = playerData.get("sync_master");
            if (syncMaster != null) {
                String[] syncSlaves;
                String syncSlaveVal = playerData.path("sync_slaves").asText();
                syncSlaves = syncSlaveVal.split(",");

                PlayerId[] slaves = new PlayerId[syncSlaves.length];
                for (int i = 0; i < slaves.length; i++) {
                    slaves[i] = new PlayerId(syncSlaves[i]);
                }
                transaction.setSyncGroup(new PlayerId(syncMaster.asText()), slaves);
            } else {
                transaction.setNoSync(newStatus.getId());
            }

            // commit all of the relevant results
            List<SimpleResult> results = sPlayerCommittableResults.removeAll(mPlayerId);
            for (SimpleResult result : results) {
                result.commit();
            }
            transaction.markSuccess();
        } catch (PlayerNotFoundException e) {
            // not a big deal
            OSLog.d("Received status for non-existent player");
        } finally {
            transaction.close();
        }
    }

    private void startTrackLookup(SBContext context, PlayerId playerId, String trackId) {
        // FIXME not sure if this is the right executor or not
        ListenableFuture<? extends TrackInfo> futureInfo = TrackInfo.load(context.getServerId(), trackId, OSExecutors.getUnboundedPool());
        Futures.addCallback(futureInfo, new FutureCallback<TrackInfo>() {
            @Override
            public void onFailure(Throwable e) {
                OSLog.i("Track lookup failure for id: " + trackId, e);
            }

            @Override
            public void onSuccess(@Nullable TrackInfo result) {
                OSAssert.assertNotNull(result, "can't be null");

                ServerStatus serverStatus = SBContextProvider.get().getServerStatus();
                Transaction transaction = serverStatus.newTransaction();
                try {
                    PlayerStatus oldStatus = serverStatus.getPlayerStatus(playerId);
                    if (oldStatus != null) {
                        PlayerStatus newStatus = oldStatus.withTrackInfo(result);
                        transaction.add(newStatus);
                        transaction.markSuccess();
                    }
                } finally {
                    transaction.close();
                }
            }
        }, MoreExecutors.newDirectExecutorService());
    }
}
