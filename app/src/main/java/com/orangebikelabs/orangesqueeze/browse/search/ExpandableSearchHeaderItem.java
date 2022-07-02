/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import android.widget.AbsListView;
import android.widget.ImageView;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.browse.common.IconRetriever;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerMenus;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.menu.MenuAction;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tsandee
 */
@ThreadSafe
public class ExpandableSearchHeaderItem extends StandardMenuItem {

    final static private ImmutableList<IconRetriever> sIconRetrievers;

    static {
        sIconRetrievers = ImmutableList.of(new RootItemIconRetriever());
    }

    @Nullable
    final private MenuAction mGoAction;

    @Nullable
    final private ExpandableSearchHeaderItem mParent;

    final private boolean mAutoExpand;

    @GuardedBy("this")
    private boolean mIsExpanded;

    @GuardedBy("this")
    private int mRemainingChildren;

    @GuardedBy("this")
    private boolean mChildrenExpanded;

    public ExpandableSearchHeaderItem(JsonNode json, MenuElement element, @Nullable MenuAction goAction, @Nullable ExpandableSearchHeaderItem parent, boolean autoExpand) {
        super(json, element, false);

        mGoAction = goAction;
        mParent = parent;
        mIsExpanded = false;
        mAutoExpand = autoExpand;

        if (parent != null) {
            parent.incrementChildCount();
        }
    }

    @Nonnull
    @Override
    public ItemType getBaseType() {
        return mAutoExpand ? ItemType.IVT_GLOBALSEARCH_HEADER : super.getBaseType();
    }

    @Nullable
    public ExpandableSearchHeaderItem getParent() {
        return mParent;
    }

    synchronized public void setChildrenExpanded(boolean val) {
        mChildrenExpanded = val;
    }

    synchronized public boolean areChildrenExpanded() {
        return mChildrenExpanded;
    }

    synchronized public void incrementChildCount() {
        mRemainingChildren++;
    }

    synchronized public void decrementChildCount() {
        mRemainingChildren--;
    }

    synchronized public int getChildCount() {
        return mRemainingChildren;
    }

    @Nullable
    public MenuAction getGoAction() {
        return mGoAction;
    }

    @Override
    public String getSectionName() {
        return null;
    }

    public String getRawText1() {
        return super.getText1();
    }

    @Nonnull
    @Override
    public String getText1() {
        if (mAutoExpand) {
            String retval = "";
            ExpandableSearchHeaderItem parent = getParent();
            if (parent != null) {
                retval += parent.getRawText1() + " | ";
            }
            retval += super.getText1();
            if (areChildrenExpanded()) {
                retval += " | Other";
            }
            return retval;
        } else {
            return super.getText1();
        }
    }

    synchronized public boolean isExpanded() {
        return mIsExpanded;
    }

    synchronized public void setExpanded(boolean val) {
        mIsExpanded = val;
    }

    public boolean shouldAutoExpand() {
        return mAutoExpand;
    }

    @Nonnull
    @Override
    public ImmutableList<IconRetriever> getIconRetrieverList() {
        return sIconRetrievers;
    }

    /**
     * icon retriever that examines the current root menu list to augment icons
     */
    static public class RootItemIconRetriever extends IconRetriever {
        @Override
        public boolean load(ThumbnailProcessor processor, Item item, AbsListView parentView, @Nullable ImageView iv) {
            SBContext sbContext = SBContextProvider.get();
            PlayerId playerId = sbContext.getPlayerId();
            if (playerId == null) {
                return false;
            }

            boolean found = false;
            ExpandableSearchHeaderItem localItem = (ExpandableSearchHeaderItem) item;
            String text = localItem.getText();
            PlayerMenus menus = sbContext.getPlayerMenus(playerId);

            Collection<MenuElement> rootMenus = menus.getRootMenus();
            for (MenuElement elem : rootMenus) {
                if (Objects.equal(elem.getText(), text)) {
                    IconRetriever ir = MenuElement.newIconRetriever(Suppliers.ofInstance(elem));

                    found = ir.load(processor, item, parentView, iv);
                    if (found) {
                        break;
                    }
                }
            }
            if (!found) {
                IconRetriever ir = MenuElement.newIconRetriever();
                found = ir.load(processor, localItem, parentView, iv);
            }
            return found;
        }

        @Override
        public boolean applies(Item item) {
            return true;
        }
    }
}
