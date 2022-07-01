/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
