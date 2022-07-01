/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
