/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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
