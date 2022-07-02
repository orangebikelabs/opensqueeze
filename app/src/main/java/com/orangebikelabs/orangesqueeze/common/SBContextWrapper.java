/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
