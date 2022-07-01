/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequest;
import com.orangebikelabs.orangesqueeze.common.LoopingRequestData;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;

import java.io.IOException;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
public class PlaylistListRequest extends BrowseRequest {
    final private boolean mUsePre76Item;

    @GuardedBy("this")
    @Nonnull
    private String mPlaylistTimestamp = "";

    @GuardedBy("this")
    private int mPlaylistIndex = -1;

    @GuardedBy("this")
    private int mTemporaryPlaylistIndex = -1;

    @GuardedBy("this")
    private boolean mInitialized;

    public PlaylistListRequest() {
        super(null);

        setCommands("status");
        addParameter("tags", "aldtxNcyKl");
        setLoopAndCountKeys(Collections.singletonList("playlist_loop"), Collections.singletonList("playlist_tracks"));

        mUsePre76Item = (mSbContext.getServerStatus().getVersion().compareTo("7.6") < 0);
    }

    @Override
    public synchronized LoopingRequestData newLoopingRequestData() {
        return new PlaylistListRequestData(this);
    }

    /**
     * if player id is absent, try to use the current player id
     */
    @Nullable
    @Override
    public PlayerId getPlayerId() {
        return mSbContext.getPlayerId();
    }

    @Override
    public synchronized void reset() {
        super.reset();

        mPlaylistIndex = -1;
        mPlaylistTimestamp = "";
        mInitialized = false;
    }

    @Override
    protected synchronized void onStartLoop(SBResult result) throws SBRequestException {
        super.onStartLoop(result);
        // do this on the first loop only
        if (!mInitialized) {
            mPlaylistTimestamp = result.getJsonResult().path("playlist_timestamp").asText();

            // save off the playlist index until we've retrieved enough items so that it's valid
            mTemporaryPlaylistIndex = result.getJsonResult().path("playlist_cur_index").asInt();
            mInitialized = true;
        }
    }

    @Override
    protected synchronized void onFinishLoop(SBResult result) throws SBRequestException {
        super.onFinishLoop(result);

        mPlaylistIndex = mTemporaryPlaylistIndex;
    }

    @Override
    protected void onLoopItem(SBResult result, ObjectNode item) throws SBRequestException {
        try {
            MenuElement element = MenuElement.get(item, getMenuBase());
            PlaylistItem listItem;

            if (mUsePre76Item) {
                listItem = new PlaylistItemPre76(item, element);
            } else {
                listItem = new PlaylistItem(item, element);
            }

            mItemList.add(listItem);
        } catch (IOException e) {
            Reporting.report(e, "Error handling menu item", item);
        }
    }

    @Nonnull
    synchronized public String getPlaylistTimestamp() {
        return mPlaylistTimestamp;
    }

    synchronized public int getPlaylistIndex() {
        return mPlaylistIndex;
    }
}
