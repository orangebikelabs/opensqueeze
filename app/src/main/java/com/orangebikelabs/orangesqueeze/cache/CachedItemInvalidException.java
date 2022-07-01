/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.orangebikelabs.orangesqueeze.common.CacheContent.ItemStatus;

/**
 * @author tsandee
 */
public class CachedItemInvalidException extends CachedItemStatusException {
    public CachedItemInvalidException(String msg) {
        super(ItemStatus.INVALID, msg);
    }

    public CachedItemInvalidException(String msg, Throwable t) {
        super(ItemStatus.INVALID, msg, t);
    }
}
