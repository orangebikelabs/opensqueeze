/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import com.fasterxml.jackson.databind.JsonNode;

import javax.annotation.Nonnull;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
public interface SBResult {
    /**
     * whether or not the job has been completed AND its changes have propagated back to us
     */
     boolean isCommitted();

    @Nonnull
     JsonNode getJsonResult();
}
