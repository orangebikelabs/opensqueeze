/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.node;

import android.os.Bundle;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.browse.common.IconRetriever;
import com.orangebikelabs.orangesqueeze.common.NavigationCommandSet;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerMenus;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.menu.ActionNames;
import com.orangebikelabs.orangesqueeze.menu.MenuAction;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */

public class NodeItem extends StandardMenuItem {

    static final public String HOME_NODE = "home";
    static final public String EXTRAS_NODE = "extras";

    final static private ImmutableList<IconRetriever> sIconRetrievers;

    static {
        sIconRetrievers = ImmutableList.of(MenuElement.newIconRetriever());
    }

    @Nonnull
    static public List<NodeItem> getRootMenu(PlayerId playerId) {
        return getMenu(playerId, HOME_NODE);
    }

    @Nonnull
    static public List<NodeItem> getMenu(PlayerId playerId, String rootNode) {

        ArrayList<NodeItem> retval = new ArrayList<>();

        final boolean forHome = rootNode.equals(HOME_NODE);
        List<String> nodeOrder;
        if (forHome) {
            nodeOrder = SBContextProvider.get().getRootBrowseNodes();
        } else {
            nodeOrder = Collections.emptyList();
        }
        final List<String> finalNodeOrder = nodeOrder;

        MenuElement extrasNode = null;

        PlayerMenus menus = SBContextProvider.get().getPlayerMenus(playerId);
        Collection<MenuElement> allItems = menus.getRootMenus();
        boolean extrasAdded = false;

        for (MenuElement e : allItems) {
            String node = e.getNode();
            String id = e.getId();

            if (node == null || id == null || e.isBlacklisted()) {
                // skip these nodes
                continue;
            }

            // stash the extras node in case we need to add it
            if (id.equals(EXTRAS_NODE)) {
                extrasNode = e;
            }

            if ((!forHome && node.equals(rootNode)) ||
                    (forHome && nodeOrder.isEmpty() && node.equals(rootNode)) ||
                    (forHome && nodeOrder.contains(id))) {

                if (id.equals(EXTRAS_NODE)) {
                    extrasAdded = true;
                }
                retval.add(new NodeItem(id, e, forHome));
            }
        }

        // explicitly add the extras node on the home menu if it's needed and it doesn't already exist
        if (forHome && nodeOrder.isEmpty() && !extrasAdded && extrasNode != null) {
            retval.add(new NodeItem(EXTRAS_NODE, extrasNode, true));
        }

        Collections.sort(retval, new Comparator<NodeItem>() {

            @Override
            public int compare(NodeItem lhs, NodeItem rhs) {
                MenuElement lhsME = lhs.getMenuElement();
                MenuElement rhsME = rhs.getMenuElement();

                int diff = compareNodeOrder(finalNodeOrder, lhsME.getId(), rhsME.getId());
                if (diff == 0) {
                    diff = Double.compare(lhsME.getWeight(), rhsME.getWeight());
                }
                if (diff == 0) {
                    String text1 = lhs.getMenuElement().getText();
                    String text2 = rhs.getMenuElement().getText();

                    diff = text1.compareTo(text2);
                }
                return diff;
            }

            private int compareNodeOrder(List<String> nodeOrder, @Nullable String lhsId, @Nullable String rhsId) {

                int lhsNdx = nodeOrder.indexOf(lhsId);
                if (lhsNdx == -1) {
                    lhsNdx = Integer.MAX_VALUE;
                }
                int rhsNdx = nodeOrder.indexOf(rhsId);
                if (rhsNdx == -1) {
                    rhsNdx = Integer.MAX_VALUE;
                }
                return lhsNdx - rhsNdx;
            }
        });
        return retval;
    }

    @Nonnull
    final private String mId;

    public NodeItem(String id, MenuElement elem, boolean forHome) {
        super(MissingNode.getInstance(), elem, forHome);
        mId = id;
    }

    @Nonnull
    public String getId() {
        return mId;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getSectionName() {
        return null;
    }

    @Override
    @Nonnull
    public ImmutableList<IconRetriever> getIconRetrieverList() {
        return sIconRetrievers;
    }

    @Override
    @Nonnull
    public String getText1() {
        return getText();
    }

    @Nonnull
    @Override
    public String getText() {

        String text = null;
        if (mForHome) {
            text = mMenuElement.getHomeMenuText();
        }
        if (text == null) {
            text = mMenuElement.getText();
        }
        return text;
    }

    @Nonnull
    public Bundle getArgumentBundle(@Nullable Bundle previousArgs) {
        Bundle retval = new Bundle();

        PlayerId fillPlayerId = null;
        NavigationItem oldItem = NavigationItem.Companion.getNavigationItem(previousArgs);
        if (oldItem != null) {
            fillPlayerId = oldItem.getPlayerId();
        }

        if (isNode()) {
            NavigationItem item = NavigationItem.Companion.newBrowseNodeItem(getItemTitle(), mId, false, fillPlayerId);
            NavigationItem.Companion.putNavigationItem(retval, item);
        } else {
            MenuAction action = mMenuElement.getActions().get(ActionNames.DO);
            if (action == null) {
                action = mMenuElement.getActions().get(ActionNames.GO);
            }
            if (action != null) {
                ArrayList<String> params = new ArrayList<>();
                for (Map.Entry<String, String> entry : action.getParams().entrySet()) {
                    params.add(entry.getKey() + ":" + entry.getValue());
                }

                NavigationCommandSet ncs = new NavigationCommandSet(action.getCommands(), params);
                NavigationItem item = NavigationItem.Companion.newBrowseRequestItem(getItemTitle(), ncs, mId, null, null, null, false, fillPlayerId);
                NavigationItem.Companion.putNavigationItem(retval, item);
            } else {
                Reporting.report(null, "Unsupported handler for root menu item", mMenuElement);
            }
        }
        return retval;
    }
}
