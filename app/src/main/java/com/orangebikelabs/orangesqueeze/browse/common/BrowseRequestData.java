/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import com.orangebikelabs.orangesqueeze.common.LoopingRequestData;
import com.orangebikelabs.orangesqueeze.menu.MenuBase;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tsandee
 */
@ThreadSafe
public class BrowseRequestData extends LoopingRequestData {
    @Nonnull
    final private List<Item> mItemList;

    final private boolean mSorted;

    @Nonnull
    final private MenuBase mMenuBase;

    public BrowseRequestData(BrowseRequest br) {
        super(br);

        mItemList = new ArrayList<>(br.getBackingItemList());

        mSorted = br.isSorted();
        mMenuBase = br.getMenuBase();
    }

    @Nonnull
    public List<Item> getItemList() {
        return mItemList;
    }

    public boolean isSorted() {
        return mSorted;
    }

    @Nonnull
    public MenuBase getMenuBase() {
        return mMenuBase;
    }
}
