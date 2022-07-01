/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.database.ContentObserver;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.TrackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tsandee
 */
@ThreadSafe
class DownloadTracksLoaderState {
    @Nonnull
    final private ConnectionInfo mConnectedInfo;

    @Nonnull
    final private List<String> mCommands;

    @Nonnull
    final private List<String> mParams;

    @Nonnull
    final private PlayerId mPlayerId;

    @Nonnull
    final private ContentObserver mObserver;

    final private AtomicInteger mOutstandingDiscoveryTasks = new AtomicInteger();
    final protected AtomicInteger mOutstandingCompletionTasks = new AtomicInteger();

    @GuardedBy("this")
    private boolean mIsInitialRequestFlag;

    @GuardedBy("this")
    final private Map<String, DownloadTrack> mTrackMap = new LinkedHashMap<>();

    @GuardedBy("this")
    final private Set<Object> mVisitedNodes = new HashSet<>();

    @GuardedBy("this")
    @Nullable
    private ListeningExecutorService mLimitedExecutor;

    public DownloadTracksLoaderState(List<String> commands, List<String> parameters, PlayerId playerId, ContentObserver observer) {
        mPlayerId = playerId;
        mConnectedInfo = SBContextProvider.get().getConnectionInfo();
        mCommands = commands;
        mParams = parameters;
        mObserver = observer;
    }

    @Nonnull
    public PlayerId getPlayerId() {
        return mPlayerId;
    }

    @Nonnull
    public ConnectionInfo getConnectionInfo() {
        return mConnectedInfo;
    }

    @Nonnull
    public List<String> getParameters() {
        return mParams;
    }

    public boolean isDiscoveryComplete() {
        return mOutstandingDiscoveryTasks.get() == 0;
    }

    @Nonnull
    synchronized public ListeningExecutorService getExecutor() {
        if (mLimitedExecutor == null) {
            mLimitedExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4, new ThreadFactory() {
                @Override
                @Nonnull
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            }));
        }

        return mLimitedExecutor;
    }

    synchronized public void cleanup() {
        if (mLimitedExecutor != null) {
            // just cancel existing tasks, we'll never need them
            mLimitedExecutor.shutdownNow();
        }
    }

    synchronized public List<DownloadTrack> getReadyTrackList() {
        // create copy to avoid concurrent modification exception
        List<DownloadTrack> retval = new ArrayList<>();

        for (DownloadTrack t : mTrackMap.values()) {
            if (t.isReady()) {
                retval.add(t);
            }
        }
        return retval;
    }

    synchronized public int getTotalTrackCount() {
        return mTrackMap.size();
    }

    @Nonnull
    public AtomicInteger getOutstandingDiscoveryTasks() {
        return mOutstandingDiscoveryTasks;
    }

    @Nonnull
    public List<String> getCommands() {
        return mCommands;
    }

    synchronized public boolean addVisitedNode(List<String> commands, List<String> params) {
        Object nodeId = calculateNodeId(commands, params);
        return mVisitedNodes.add(nodeId);
    }

    synchronized public boolean addDownloadElement(final DownloadTrack elem) {
        boolean retval = false;
        if (!mTrackMap.containsKey(elem.getId())) {
            mTrackMap.put(elem.getId(), elem);

            // trigger trackinfo lookup for the download element
            mOutstandingCompletionTasks.incrementAndGet();
            ListenableFuture<? extends TrackInfo> futureTrackInfo = TrackInfo.load(mConnectedInfo.getServerId(), elem.getId(), getExecutor());
            Futures.addCallback(futureTrackInfo, new FutureCallback<TrackInfo>() {

                @Override
                public void onFailure(@Nullable Throwable t) {
                    elem.markError(t == null ? "error" : t.getMessage());
                    OSLog.w("Error loading track info for track id: " + elem.getId(), t);
                    complete();
                }

                @Override
                public void onSuccess(@Nullable TrackInfo ti) {
                    OSAssert.assertNotNull(ti, "trackinfo shouldn't be null here");

                    elem.setTrackInfo(ti);
                    complete();
                }

                private void complete() {
                    mOutstandingCompletionTasks.decrementAndGet();
                    notifyObservers();
                }
            }, MoreExecutors.directExecutor());
            retval = true;
        }
        return retval;
    }

    public void notifyObservers() {
        mObserver.dispatchChange(true, null);
    }

    synchronized public boolean isInitialRequestMade() {
        return mIsInitialRequestFlag;
    }

    /**
     * returns true if this is the first time this has been called, false otherwise
     */
    synchronized public boolean takeInitialRequestFlag() {
        if (!mIsInitialRequestFlag) {
            mIsInitialRequestFlag = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * get a node ID that will be used to avoid recursive calls
     */
    @Nonnull
    private Object calculateNodeId(List<String> commands, List<String> params) {
        return Lists.newArrayList(Iterables.concat(commands, params));
    }
}
