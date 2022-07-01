/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app;

import androidx.annotation.Keep;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.common.SBResult;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

/**
 * @author tsandee
 */
@Keep
public class SimpleResult implements SBResult {
    final private JsonNode mResult;

    @GuardedBy("this")
    private boolean mCommitted = false;

    public SimpleResult(JsonNode result) {
        mResult = result;
    }

    @Override
    @Nonnull
    public JsonNode getJsonResult() {
        return mResult;
    }

    @Override
    synchronized public boolean isCommitted() {
        return mCommitted;
    }

    synchronized public void commit() {
        mCommitted = true;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("committed", isCommitted())
                .add("result", mResult)
                .toString();
    }
}
