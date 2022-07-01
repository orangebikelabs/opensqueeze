/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.loader.content.Loader;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/**
 * Loader that observes a looping request.
 * <p/>
 * Uses the LoopingRequest.Holder class as a marker to force loader updates.
 * <p/>
 *
 * @author tbsandee@orangebikelabs.com
 */
public class LoopingRequestLoader<T extends LoopingRequestData> extends Loader<T> {
    final private static AtomicInteger sInstanceCounter = new AtomicInteger();

    final protected ForceLoadContentObserver mObserver;
    final protected int mLoaderInstance = sInstanceCounter.getAndIncrement();
    final protected LoopingRequest mRequest;

    public LoopingRequestLoader(LoopingRequest request) {
        super(SBContextProvider.get().getApplicationContext());

        mRequest = request;

        mObserver = new ForceLoadContentObserver();
        mRequest.registerObserver(mObserver);

        logVerbose("create");
    }

    @Override
    protected void onReset() {
        logVerbose("onReset");

        mRequest.reset();

        super.onReset();
    }

    @Override
    protected void onStartLoading() {
        logVerbose("onStartLoading");

        mRequest.submit(OSExecutors.getUnboundedPool());

        // in a few situations, deliver results immediately
        T results = transformRequestResults();
        if (results.isComplete() || results.getPosition() > 0) {
            deliverResult(results);
        }

        if (takeContentChanged()) {
            forceLoad();
        }
    }

    @SuppressWarnings("unchecked")
    private T transformRequestResults() {
        return (T) mRequest.newLoopingRequestData();
    }

    /**
     * Runs on the UI thread
     */
    @Override
    public void deliverResult(@Nullable T requestData) {
        logVerbose("deliverResult");

        if (isReset()) {
            if (requestData != null) {
                // async request came in while reset
                requestData.getRequest().reset();
            }
            return;
        }

        if (isStarted()) {
            super.deliverResult(requestData);
        }
    }

    @Override
    protected void onStopLoading() {
        logVerbose("onStopLoading");

        mRequest.stop();
    }

    @Override
    protected void onForceLoad() {

        logVerbose("onForceLoad");
        super.onForceLoad();

        if (mRequest.isStarted() || mRequest.isComplete()) {
            // deliver existing data
            deliverResult(transformRequestResults());
        } else {
            // not started, go ahead and start it
            mRequest.submit(OSExecutors.getUnboundedPool());
        }
    }

    final protected void logVerbose(String message) {
        if (OSLog.isLoggable(OSLog.Tag.LOADERS, OSLog.VERBOSE)) {
            OSLog.v(OSLog.Tag.LOADERS, "LoopingRequestLoader:" + mRequest.getClass().getSimpleName() + ":" + mLoaderInstance + " " + message);
        }
    }
}
