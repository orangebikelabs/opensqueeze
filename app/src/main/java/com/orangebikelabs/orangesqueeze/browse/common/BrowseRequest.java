/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Atomics;
import com.orangebikelabs.orangesqueeze.common.LoopingRequest;
import com.orangebikelabs.orangesqueeze.common.LoopingRequestData;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.menu.MenuBase;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
public class BrowseRequest extends LoopingRequest {

    /**
     * after LOTS of waffling, finally settled on using a synchronized list for this. too many situations where we need to use it from
     * several threads, etc
     */

    @GuardedBy("mItemList")
    final protected List<Item> mItemList = Collections.synchronizedList(new ArrayList<>());

    // the following blocks use atomic variables just to simplify some concurrency issues and reduce locking on the request object
    final private AtomicBoolean mIsSorted = new AtomicBoolean();
    final private AtomicBoolean mSortedInit = new AtomicBoolean();
    final private AtomicReference<MenuBase> mMenuBase = Atomics.newReference();

    /**
     * for subclasses only
     */
    public BrowseRequest(@Nullable PlayerId playerId) {
        super(playerId);
    }

    @Override
    public synchronized LoopingRequestData newLoopingRequestData() {
        return new BrowseRequestData(this);
    }

    @Override
    synchronized public void reset() {
        super.reset();

        // release a bit of memory
        mItemList.clear();
        mMenuBase.set(null);
        mSortedInit.set(false);
        mIsSorted.set(false);
    }

    /**
     * expose item list, caller would be advised not to change this
     */
    @Nonnull
    public List<Item> getBackingItemList() {
        return mItemList;
    }

    public MenuBase getMenuBase() {
        return mMenuBase.get();
    }

    public boolean isSorted() {
        return mIsSorted.get();
    }

    protected void addSeparators() {
        // synchronize on mItemList for this to ensure that the list size doesn't change underneath us
        synchronized (mItemList) {
            String lastSection = null;
            int max = mItemList.size();
            for (int i = 0; i < max; i++) {
                Item item = mItemList.get(i);

                String itemSection = item.getSectionName();
                if (itemSection != null && !itemSection.equals(lastSection)) {
                    // gracefully handle a properly-placed separator item
                    if (!(item instanceof SeparatorItem)) {
                        // add new separator item and skip past it
                        mItemList.add(i, new SeparatorItem(itemSection));
                        max++;
                        i++;
                    }
                    lastSection = itemSection;
                }
            }
        }
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected void onStartLoop(SBResult result) throws SBRequestException {
        if (mMenuBase.get() == null) {
            try {
                MenuBase newMenuBase = MenuBase.get(result);
                if (mMenuBase.compareAndSet(null, newMenuBase)) {
                    Map<String, String> window = newMenuBase.getWindow();
                    String text = window.get("text");
                    if (text != null) {
                        mItemList.add(new TextItem(text));
                    }
                }
            } catch (IOException e) {
                Reporting.report(e, "Error parsing base menu structures", result.getJsonResult());
            }
        }
    }

    @Override
    protected void onLoopItem(SBResult result, ObjectNode item) throws SBRequestException {
        try {
            MenuElement element = MenuElement.get(item, getMenuBase());
            StandardMenuItem menuItem = StandardMenuItem.newInstance(this, item, element);
            if (mSortedInit.compareAndSet(false, true)) {
                mIsSorted.set(menuItem.getSectionName() != null);
            }
            mItemList.add(menuItem);
        } catch (IOException e) {
            Reporting.report(e, "Error handling menu item", item);
        }
    }
}
