/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;

import javax.annotation.Nonnull;

/**
 * Fired when it's a good time for the preload fragment to initiate a new preload.
 */
@Keep
public class TriggerListPreload {
    public TriggerListPreload() {
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).toString();
    }
}
