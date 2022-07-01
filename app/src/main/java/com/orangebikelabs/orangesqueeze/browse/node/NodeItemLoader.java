/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.node;

import android.database.ContentObserver;
import androidx.loader.content.AsyncTaskLoader;

import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.event.PlayerBrowseMenuChangedEvent;
import com.squareup.otto.Subscribe;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final public class NodeItemLoader extends AsyncTaskLoader<List<? extends Item>> {
    @Nullable
    final private PlayerId mPlayerId;

    @Nonnull
    final private ContentObserver mObserver = new ForceLoadContentObserver();

    @Nonnull
    final private String mParentNodeId;

    private boolean mRegistered = false;

    public NodeItemLoader(@Nullable PlayerId playerId, String parentNodeId) {
        super(SBContextProvider.get().getApplicationContext());

        mPlayerId = playerId;
        mParentNodeId = parentNodeId;

        // there's data right away, start loading when it's time
        mObserver.onChange(true);
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        registerBusListener();

        // clear the content changed flag, we're forcing a load
        takeContentChanged();

        // when we start loading, always rebuild the node list
        forceLoad();
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

    private void registerBusListener() {
        OSAssert.assertMainThread();

        if (!mRegistered) {
            mRegistered = true;
            BusProvider.getInstance().register(mEventReceiver);
        }
    }

    private void unregisterBusListener() {
        OSAssert.assertMainThread();

        if (mRegistered) {
            mRegistered = false;
            BusProvider.getInstance().unregister(mEventReceiver);
        }
    }

    @Override
    public List<NodeItem> loadInBackground() {
        List<NodeItem> retval = null;

        if (mPlayerId != null) {
            PlayerStatus status = SBContextProvider.get().getServerStatus().getPlayerStatus(mPlayerId);
            if (status != null) {
                retval = NodeItem.getMenu(mPlayerId, mParentNodeId);
            }
        }

        if (retval == null) {
            retval = Collections.emptyList();
        }
        return retval;
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenPlayerBrowseMenuChanges(PlayerBrowseMenuChangedEvent event) {
            if (!event.getPlayerId().equals(mPlayerId)) {
                return;
            }
            mObserver.dispatchChange(true, null);
        }
    };
}
