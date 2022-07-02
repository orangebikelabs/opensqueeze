/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.orangebikelabs.orangesqueeze.menu.ActionNames;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A preferences support class. Describes the different default play semantics.
 */
public enum PlayOptions {
    PROMPT("prompt", null), PLAY("play", ActionNames.PLAY), INSERT("insert", ActionNames.ADDHOLD), ADD("add", ActionNames.ADD);

    final private String mVal;

    @Nullable
    final private String mAction;

    PlayOptions(String str, @Nullable String action) {
        mVal = str;
        mAction = action;
    }

    @Nonnull
    public String getValue() {
        return mVal;
    }

    /**
     * returns the action to use to figure out what thing to "do"
     */
    @Nullable
    public String getAction() {
        return mAction;
    }

    @Nonnull
    static public PlayOptions fromValue(String val) {
        for (PlayOptions value : values()) {
            if (value.getValue().equals(val)) {
                return value;
            }
        }

        // set this as the default value
        return PLAY;
    }
}