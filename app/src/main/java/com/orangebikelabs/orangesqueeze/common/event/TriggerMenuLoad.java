/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;

import javax.annotation.Nonnull;

/**
 * Triggers the load of new menu data from the server.
 */
@Keep
public class TriggerMenuLoad {
    public TriggerMenuLoad() {
        // empty
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).toString();
    }
}
