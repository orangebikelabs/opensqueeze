/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
