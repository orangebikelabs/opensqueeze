/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequest;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;
import com.orangebikelabs.orangesqueeze.browse.common.TextItem;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.menu.ActionNames;
import com.orangebikelabs.orangesqueeze.menu.MenuAction;
import com.orangebikelabs.orangesqueeze.menu.MenuBase;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;
import com.orangebikelabs.orangesqueeze.menu.MenuHelpers;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class GlobalSearchRequest extends BrowseRequest {
    /**
     * this must be used only on the request thread (from onStartLoop, onLoopItem and onFinishLoop) or some sort of threadsafe collection
     * must be used
     */
    final private LinkedList<Item> mExpansionCandidates = Lists.newLinkedList();

    static protected Item createItem(BrowseRequest request, ObjectNode item, MenuBase base, @Nullable ExpandableSearchHeaderItem parent) throws IOException {

        // is this a search header item?
        MenuElement elem = MenuElement.get(item, base);

        MenuAction action = MenuHelpers.getAction(elem, ActionNames.GO);

        Item retval;
        if (action != null && action.getCommands().equals(Arrays.asList("globalsearch", "items"))) {
            // this is expandable
            boolean autoExpand = GlobalSearchPreferences.shouldExpandItem(elem, action);

            retval = new ExpandableSearchHeaderItem(item, elem, action, parent, autoExpand);
        } else {
            retval = StandardMenuItem.newInstance(request, item, elem);
        }
        return retval;
    }

    public GlobalSearchRequest(@Nullable PlayerId playerId, String searchTerm) {
        super(playerId);

        setCommands("globalsearch", "items");
        addParameter("search", searchTerm);
        addParameter("menu", "globalsearch");
        addParameter("useContextMenu", "1");
    }

    @Override
    protected void onStartLoop(SBResult result) throws SBRequestException {
        super.onStartLoop(result);

        mExpansionCandidates.clear();
    }

    @Override
    protected void onLoopItem(SBResult loopingResult, ObjectNode item) throws SBRequestException {
        try {
            mExpansionCandidates.add(createItem(this, item, getMenuBase(), null));
        } catch (IOException e) {
            // gracefully ignore non-parsable items
            Reporting.report(e, "Error handling menu item", item);
        }
    }

    @Override
    protected void onFinishLoop(SBResult loopingResult) throws SBRequestException {
        // add all of the items just received to the visible list

        mItemList.addAll(sortItemList(mExpansionCandidates));

        // iterate through the expansion candidates iterate through each one and expand them, if necessary
        Item item;
        while ((item = mExpansionCandidates.poll()) != null) {
            if (!(item instanceof ExpandableSearchHeaderItem)) {
                // not expandable
                continue;
            }

            // now try to expand the item
            ExpandableSearchHeaderItem sep = (ExpandableSearchHeaderItem) item;
            if (sep.isExpanded() || !sep.shouldAutoExpand()) {
                // nope
                continue;
            }

            // this may add items to mExpansionCandidates
            expandItem(sep);
        }

        // determine if we need to add the "other" menu
        for (int i = 0; i < mItemList.size(); i++) {
            item = mItemList.get(i);
            if (!(item instanceof ExpandableSearchHeaderItem)) {
                continue;
            }
            ExpandableSearchHeaderItem eshItem = (ExpandableSearchHeaderItem) item;
            if (eshItem.getParent() == null && !eshItem.isExpanded()) {
                // Don't put this at the top, it's inferred
                if (i > 0) {
                    mItemList.add(i, new OtherMenuItem("Other"));
                }
                break;
            }
        }
        notifyObservers();
    }

    /**
     * check to see if the specified item can be removed because it has no children
     */
    private void checkParentRemoval(ExpandableSearchHeaderItem parent) {
        boolean foundChild = false;
        for (ExpandableSearchHeaderItem eshi : Iterables.filter(mItemList, ExpandableSearchHeaderItem.class)) {
            if (eshi.getParent() == parent) {
                foundChild = true;
                break;
            }
        }
        if (!foundChild) {
            mItemList.remove(parent);

            if (parent.getParent() != null) {
                // now check one level up...
                checkParentRemoval(parent.getParent());
            }
        }
    }

    /**
     * returns true if item was expanded and still exists, false if it was removed
     */
    protected boolean expandItem(ExpandableSearchHeaderItem item) throws SBRequestException {
        item.setMutatedProgressVisible(true, null);
        try {
            MenuAction action = item.getGoAction();
            if (action == null) {
                return false;
            }

            MenuElement elem = item.getMenuElement();

            ExpandRequest expandRequest = new ExpandRequest(getPlayerId(), item);
            expandRequest.setCommands(action.getCommands());
            expandRequest.setMaxRows(GlobalSearchPreferences.getMaxRows());

            List<String> params = MenuHelpers.buildParametersAsList(elem, action, false);
            if (params == null) {
                return false;
            }

            expandRequest.setParameters(params);
            expandRequest.submit(MoreExecutors.newDirectExecutorService());
            if (expandRequest.isAborted()) {
                throw new SBRequestException("expand request failed");
            }

            boolean retval;
            List<Item> list = expandRequest.getBackingItemList();
            if (isEmptyList(list)) {
                // do nothing with it, throw it away
                mItemList.remove(item);

                if (item.getParent() != null) {
                    // can we remove the parent too?
                    checkParentRemoval(item);
                }
                retval = false;
            } else {
                // remove parent separator header, because we merge the titles to conserve space
                ExpandableSearchHeaderItem parent = item.getParent();
                if (parent != null) {
                    parent.decrementChildCount();
                    parent.setChildrenExpanded(true);

                    // remove the parent node for now, add it back later maybe
                    mItemList.remove(parent);
                }

                item.setExpanded(true);

                int ndx = mItemList.indexOf(item) + 1;
                mItemList.addAll(ndx, sortItemList(list));
                ndx += list.size();

                int remaining = expandRequest.getTotalRecordCount() - list.size();
                if (remaining > 0) {
                    String text = mContext.getString(R.string.globalsearch_moreitem, remaining);
                    mItemList.add(ndx++, new OverrideTextMenuItem(item.getNode(), item.getMenuElement(), text));
                }

                if (parent != null && parent.getChildCount() > 0) {
                    // add parent node at end of list, there are still children
                    mItemList.add(ndx++, parent);
                }

                // put these expansion candidates at the front of queue
                mExpansionCandidates.addAll(0, list);
                retval = true;
            }
            return retval;
        } finally {
            item.setMutatedProgressVisible(false, null);
        }
    }

    /**
     * is this list considered an empty list? Then skip it
     */
    private boolean isEmptyList(List<Item> list) {
        boolean retval = false;

        if (list.isEmpty()) {
            retval = true;
        } else if (list.size() == 1) {
            // single text items are considered "Empty"
            Item item = list.get(0);
            retval = item.isSingleItemConsideredEmpty();
        }
        return retval;
    }

    /**
     * reorders item list so that expanding items will be first in the list, because presumably the user is more interested in those
     */
    private List<Item> sortItemList(List<Item> items) {
        ArrayList<Item> temp = new ArrayList<>();
        ArrayList<Item> retval = Lists.newArrayListWithExpectedSize(items.size());

        for (Item item : items) {
            boolean expandable = false;
            if (item instanceof ExpandableSearchHeaderItem) {
                ExpandableSearchHeaderItem ei = (ExpandableSearchHeaderItem) item;
                expandable = ei.shouldAutoExpand();
            }

            if (expandable) {
                retval.add(item);
            } else {
                temp.add(item);
            }
        }

        retval.addAll(temp);
        return retval;
    }

    /**
     * special request that is used to expand search items inline
     */
    static class ExpandRequest extends BrowseRequest {
        final private ExpandableSearchHeaderItem mParent;

        public ExpandRequest(@Nullable PlayerId playerId, ExpandableSearchHeaderItem parent) {
            super(playerId);

            mParent = parent;
        }

        @Override
        protected synchronized void onLoopItem(SBResult result, ObjectNode item) throws SBRequestException {
            try {
                mItemList.add(GlobalSearchRequest.createItem(this, item, getMenuBase(), mParent));
            } catch (IOException e) {
                // gracefully ignore non-parseable items
                Reporting.report(e, "Error handling menu item", item);
            }
        }
    }

    /**
     * this is for the overflow items ("5 more items)
     */
    static class OverrideTextMenuItem extends StandardMenuItem {
        final private String mText;

        public OverrideTextMenuItem(JsonNode json, MenuElement element, String text) {
            super(json, element, false);

            mText = text;
        }

        @Override
        @Nonnull
        public String getText1() {
            return mText;
        }
    }

    /**
     * this is the root menu item, only visible if there are auto-expanded items
     */
    static class OtherMenuItem extends TextItem {
        public OtherMenuItem(String text) {
            super(text);
        }

        @Override
        @Nonnull
        public ItemType getBaseType() {
            return ItemType.IVT_GLOBALSEARCH_OTHER_HEADER;
        }
    }
}
