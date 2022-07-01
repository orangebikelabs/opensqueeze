/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A holder class used to transfer data between a looping request and a loader.
 *
 * @author tsandee
 */
@Keep
@ThreadSafe
public class LoopingRequestData {

    @Nonnull
    final private LoopingRequest mRequest;

    final private int mPosition;

    final private int mTotalCount;

    @Nullable
    final private FutureResult mLastResult;

    final private boolean mComplete;

    public LoopingRequestData(LoopingRequest request) {
        mRequest = request;

        mPosition = request.getPosition();
        mTotalCount = request.getTotalRecordCount();
        mLastResult = request.getLastResult();
        mComplete = request.isComplete();
    }

    @Nonnull
    public LoopingRequest getRequest() {
        return mRequest;
    }

    public boolean isComplete() {
        return mComplete;
    }

    public int getTotalCount() {
        return mTotalCount;
    }

    public int getPosition() {
        return mPosition;
    }

    @Nullable
    public FutureResult getLastResult() {
        return mLastResult;
    }
}
