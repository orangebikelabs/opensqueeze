/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
@Keep
public class AppPreferenceChangeEvent {
    final private String mKey;
    final private boolean mTriggerRestart;

    public AppPreferenceChangeEvent(String key, boolean triggerRestart) {
        mKey = key;
        mTriggerRestart = triggerRestart;
    }

    @Nonnull
    public String getKey() {
        return mKey;
    }

    public boolean shouldTriggerRestart() {
        return mTriggerRestart;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("key", mKey)
                .add("triggerRestart", mTriggerRestart)
                .toString();
    }
}
