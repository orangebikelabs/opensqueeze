/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.content.Context;
import androidx.loader.content.AsyncTaskLoader;

import com.orangebikelabs.orangesqueeze.common.PlayerId;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * Custom loader that executes a series of queries to calculate the list of downloadable tracks. The queries are run in parallel and the
 * results are sent throttled every 500ms at most.
 * <p/>
 * The download element has an additional field "selected" that is used to track the checkbox state. It is not used or populated by the
 * loader.
 *
 * @author tsandee
 */
public class DownloadTracksLoader extends AsyncTaskLoader<DownloadJob> {
    final private ForceLoadContentObserver mObserver = new ForceLoadContentObserver();

    @Nonnull
    final private List<String> mCommands;

    @Nonnull
    final private List<String> mParameters;

    @Nonnull
    final private PlayerId mPlayerId;

    @Nonnull
    private DownloadTracksLoaderState mStateSnapshot;

    public DownloadTracksLoader(Context context, List<String> commands, List<String> parameters, PlayerId playerId) {
        super(context);

        mCommands = commands;
        mParameters = parameters;
        mPlayerId = playerId;
        mStateSnapshot = newStateSnapshot();

        setUpdateThrottle(500);
    }

    @Override
    protected void onReset() {
        super.onReset();

        mStateSnapshot.cleanup();
        mStateSnapshot = newStateSnapshot();
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        if (!mStateSnapshot.isInitialRequestMade()) {
            forceLoad();
        }
    }

    @Override
    @Nonnull
    public DownloadJob loadInBackground() {
        DownloadTracksCallable callable = new DownloadTracksCallable(mStateSnapshot);
        return callable.call();
    }

    @Nonnull
    private DownloadTracksLoaderState newStateSnapshot() {
        return new DownloadTracksLoaderState(mCommands, mParameters, mPlayerId, mObserver);
    }
}
