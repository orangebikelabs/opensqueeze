/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class SimpleLoopingRequest extends LoopingRequest {
    public SimpleLoopingRequest(@Nullable PlayerId playerId) {
        super(playerId);
    }
}
