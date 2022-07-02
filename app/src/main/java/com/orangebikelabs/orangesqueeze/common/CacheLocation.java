/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import com.orangebikelabs.orangesqueeze.R;

import javax.annotation.Nonnull;

/**
 * A preferences support class. Denotes the location of the cache information.
 */
@Keep
public enum CacheLocation {
    INTERNAL("internal", R.string.cachelocation_internal), EXTERNAL("external", R.string.cachelocation_external);

    final private String mVal;
    final private int mRid;

    CacheLocation(String str, int rid) {
        mVal = str;
        mRid = rid;
    }

    @Nonnull
    public String getKey() {
        return mVal;
    }

    public int getRid() {
        return mRid;
    }

    @Nonnull
    static public CacheLocation fromKey(String key) {
        for (CacheLocation value : values()) {
            if (value.getKey().equals(key)) {
                return value;
            }
        }

        // set this as the default
        return INTERNAL;
    }
}