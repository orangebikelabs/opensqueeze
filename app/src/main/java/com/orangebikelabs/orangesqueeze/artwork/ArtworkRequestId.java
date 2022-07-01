/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Artwork request ID generator.
 */
public class ArtworkRequestId {

    static final private AtomicInteger sRequestId = new AtomicInteger(1);

    /**
     * return next artwork request id
     */
    static public int next() {
        return sRequestId.getAndIncrement();
    }
}
