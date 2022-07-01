/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public interface SBRequest {

    long DEFAULT_REQUEST_TIMEOUT = 60;
    TimeUnit DEFAULT_REQUEST_TIMEOUT_UNITS = TimeUnit.SECONDS;

    enum CommitType {
        IMMEDIATE, PLAYERUPDATE
    }

    enum Type {
        JSONRPC, COMET
    }

    @Nonnull
    SBRequest setPlayerId(@Nullable PlayerId playerId);

    @Nullable
    PlayerId getPlayerId();

    @Nonnull
    SBRequest setCommitType(CommitType commitType);

    @Nonnull
    CommitType getCommitType();

    @Nonnull
    List<?> getCommands();

    @Nonnull
    SBRequest setCacheable(boolean cacheable);

    boolean isCacheable();

    boolean shouldRefreshCache();

    @Nonnull
    SBRequest setShouldRefreshCache(boolean refresh);

    @Nonnull
    SBRequest setMaxRows(int maxRows);

    int getMaxRows();

    /**
     * submits result with the supplied executorservice
     */
    @Nonnull
    FutureResult submit(ListeningExecutorService executorService);
}
