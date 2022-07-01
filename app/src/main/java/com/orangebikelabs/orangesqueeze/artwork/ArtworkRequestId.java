/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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
