/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.orangebikelabs.orangesqueeze.common.CacheContent.ItemStatus;

/**
 * @author tsandee
 */
public class CachedItemNotFoundException extends CachedItemStatusException {
    public CachedItemNotFoundException(String msg) {
        super(ItemStatus.NOTFOUND, msg);
    }
}
