/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.orangebikelabs.orangesqueeze.common.CacheContent.ItemStatus;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
abstract public class CachedItemStatusException extends SBCacheException {
    @Nonnull
    final private ItemStatus mItemStatus;

    protected CachedItemStatusException(ItemStatus itemState, String msg) {
        super(msg);
        mItemStatus = itemState;
    }

    protected CachedItemStatusException(ItemStatus itemState, String msg, Throwable t) {
        super(msg, t);
        mItemStatus = itemState;
    }

    @Nonnull
    public ItemStatus getItemStatus() {
        return mItemStatus;
    }
}
