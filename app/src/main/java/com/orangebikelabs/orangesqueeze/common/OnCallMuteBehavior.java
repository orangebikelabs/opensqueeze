/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import javax.annotation.Nonnull;

/**
 * A preferences support class. Denotes the behavior of the app when a call is received.
 */
@Keep
public enum OnCallMuteBehavior {
    NOTHING("nothing"), MUTE("mute"), PAUSE("pause"), MUTE_CURRENT("mutecurrent"), PAUSE_CURRENT("pausecurrent");

    final private String mVal;

    OnCallMuteBehavior(String str) {
        mVal = str;
    }

    public String getValue() {
        return mVal;
    }

    @Nonnull
    static OnCallMuteBehavior fromValue(String val) {
        for (OnCallMuteBehavior value : values()) {
            if (value.getValue().equals(val)) {
                return value;
            }
        }

        // set this as the default value
        return NOTHING;
    }
}