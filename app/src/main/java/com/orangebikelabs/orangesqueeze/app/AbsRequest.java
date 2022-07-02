/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
abstract public class AbsRequest implements SBRequest, Callable<SBResult> {
    /**
     * cannot use ImmutableList because the commands *may* contain null
     */
    @Nonnull
    final protected List<Object> mCommands;

    @GuardedBy("this")
    private long mTimeoutMillis = 30000;

    @GuardedBy("this")
    @Nullable
    private PlayerId mPlayerId;

    @GuardedBy("this")
    @Nonnull
    private CommitType mCommitType = CommitType.IMMEDIATE;

    @GuardedBy("this")
    private boolean mCacheable;

    @GuardedBy("this")
    private boolean mRefreshCache;

    @GuardedBy("this")
    private int mMaxRows;

    protected AbsRequest(List<?> commands) {
        mCommands = ImmutableList.copyOf(commands);
    }

    @Override
    @Nonnull
    public FutureResult submit(ListeningExecutorService executorService) {
        ListenableFuture<SBResult> future = executorService.submit(this);
        return FutureResult.result(future);
    }

    @Override
    @Nonnull
    public List<Object> getCommands() {
        return mCommands;
    }

    @Nonnull
    public synchronized SBRequest setTimeout(long timeout, TimeUnit units) {
        mTimeoutMillis = units.toMillis(timeout);
        return this;
    }

    public synchronized long getTimeoutMillis() {
        return mTimeoutMillis;
    }

    @Override
    @Nullable
    public synchronized PlayerId getPlayerId() {
        return mPlayerId;
    }

    @Override
    @Nonnull
    public synchronized SBRequest setPlayerId(@Nullable PlayerId playerId) {
        mPlayerId = playerId;
        return this;
    }

    @Override
    synchronized public boolean isCacheable() {
        return mCacheable;
    }

    @Override
    @Nonnull
    synchronized public SBRequest setCacheable(boolean cacheable) {
        mCacheable = cacheable;
        return this;
    }

    @Override
    synchronized public boolean shouldRefreshCache() {
        return mRefreshCache;
    }

    @Override
    @Nonnull
    synchronized public SBRequest setShouldRefreshCache(boolean refresh) {
        mRefreshCache = refresh;
        return this;
    }

    @Override
    public synchronized int getMaxRows() {
        return mMaxRows;
    }

    @Override
    @Nonnull
    public synchronized SBRequest setMaxRows(int maxRows) {
        mMaxRows = maxRows;
        return this;
    }

    @Nonnull
    @Override
    public synchronized SBRequest setCommitType(CommitType commitType) {
        mCommitType = commitType;
        return this;
    }

    @Override
    @Nonnull
    public synchronized CommitType getCommitType() {
        return mCommitType;
    }

    @Override
    @Nonnull
    abstract public SBResult call() throws SBRequestException, InterruptedException;

    @Nonnull
    protected MoreObjects.ToStringHelper getBaseToStringHelper() {
        // @formatter:off
        return MoreObjects.toStringHelper(this).

                add("commands", getCommands()).
                add("commitType", getCommitType()).
                add("playerId", getPlayerId()).
                add("cacheable", isCacheable()).
                add("maxRows", getMaxRows());
        // @formatter:on
    }

    @Override
    @Nonnull
    public String toString() {
        return getBaseToStringHelper().toString();
    }
}
