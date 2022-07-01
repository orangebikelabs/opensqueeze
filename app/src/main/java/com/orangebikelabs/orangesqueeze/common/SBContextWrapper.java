/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import com.orangebikelabs.orangesqueeze.app.MutableWrapper;

/**
 * @author tsandee
 */
@Keep // because used with reflection
public interface SBContextWrapper extends SBContext, MutableWrapper<SBContext> {
    // intentionally blank
}
