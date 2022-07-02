/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.content.Context;
import android.content.Intent;

import com.orangebikelabs.orangesqueeze.BuildConfig;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.event.AnyPlayerStatusEvent;
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState;
import com.squareup.otto.Subscribe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered as a eventbus listener to broadcast standard media metadata/player state events via intents.
 */
public class BroadcastMediaMetadataIntents {

    @Nonnull
    final private Context mApplicationContext;

    public BroadcastMediaMetadataIntents(Context context) {
        Context applicationContext = context.getApplicationContext();
        OSAssert.assertNotNull(applicationContext, "application context shouldn't be null");

        mApplicationContext = applicationContext;
    }

    @Subscribe
    public void whenAnyPlayerChange(AnyPlayerStatusEvent event) {
        String prefix = BuildConfig.APPLICATION_ID + ".all";

        broadcast(prefix, event.getPlayerStatus(), event.getPreviousPlayerStatus());
    }

    @Subscribe
    public void whenCurrentPlayerChange(CurrentPlayerState event) {
        String prefix = BuildConfig.APPLICATION_ID;

        PlayerStatus current = event.getPlayerStatus();
        if (current != null) {
            broadcast(prefix, current, event.getPreviousPlayerStatus());
        }
    }

    private void broadcast(String prefix, PlayerStatus currentStatus, @Nullable PlayerStatus previousStatus) {
        // send updated metadata, always

        Intent metadataIntent = populateIntent(prefix, "metachanged", currentStatus);
        mApplicationContext.sendBroadcast(metadataIntent);

        if (previousStatus == null || !currentStatus.getMode().equals(previousStatus.getMode())) {
            // send playerstate changed

            Intent playstatechangedIntent = populateIntent(prefix, "playstatechanged", currentStatus);
            mApplicationContext.sendBroadcast(playstatechangedIntent);
        }
    }

    @Nonnull
    private Intent populateIntent(String prefix, String action, PlayerStatus status) {
        Intent retval = new Intent(prefix + "." + action);
        retval.putExtra("track", status.getTrack());
        String trackNumber = status.getTrackNumber().orNull();
        if (trackNumber != null) {
            retval.putExtra("tracknumber", trackNumber);
        }
        retval.putExtra("album", status.getAlbum());
        retval.putExtra("playerid", status.getId().toString());
        retval.putExtra("playername", status.getName());
        retval.putExtra("artist", status.getDisplayArtist());
        retval.putExtra("trackartist", status.getTrackArtist());
        retval.putExtra("albumartist", status.getArtist());
        retval.putExtra("position", (int) (status.getElapsedTime(false) * 1000));
        retval.putExtra("duration", (int) (status.getTotalTime() * 1000));
        retval.putExtra("ispaused", status.getMode() == PlayerStatus.Mode.PAUSED);
        retval.putExtra("isplaying", status.getMode() == PlayerStatus.Mode.PLAYING);
        retval.putExtra("remote", status.isRemote());
        retval.putExtra("current_title", status.getCurrentTitle());
        return retval;
    }
}
