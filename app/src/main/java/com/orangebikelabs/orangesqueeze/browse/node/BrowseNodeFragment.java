/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.node;

import android.content.Intent;
import android.os.Bundle;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import android.view.View;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.browse.AbsBrowseFragment;
import com.orangebikelabs.orangesqueeze.browse.OSBrowseAdapter;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.NavigationManager;
import com.orangebikelabs.orangesqueeze.common.event.ActivePlayerChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.ConnectionStateChangedEvent;
import com.orangebikelabs.orangesqueeze.menu.ActionNames;
import com.orangebikelabs.orangesqueeze.menu.MenuAction;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;
import com.orangebikelabs.orangesqueeze.menu.MenuHelpers;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;
import com.squareup.otto.Subscribe;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fragment used for node-based browsing where the browse is from a list of nodes stored in-memory.
 *
 * @author tsandee
 */
public class BrowseNodeFragment extends AbsBrowseFragment<NodeItemAdapter, List<? extends Item>> {
    @Nonnull
    static public BrowseNodeFragment newInstance(NavigationItem item) {
        BrowseNodeFragment retval = new BrowseNodeFragment();
        Bundle args = new Bundle();
        NavigationItem.Companion.putNavigationItem(args, item);
        retval.setArguments(args);
        return retval;
    }

    public BrowseNodeFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BusProvider.getInstance().register(mEventReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        BusProvider.getInstance().unregister(mEventReceiver);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getBrowseListView() != null) {
            getBrowseListView().setFastScrollAlwaysVisible(false);
            getBrowseListView().setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
            getBrowseListView().setFastScrollEnabled(false);
        }
    }

    @Override
    @Nonnull
    protected LoaderCallbacks<List<? extends Item>> createLoaderCallbacks() {
        return new NodeLoaderCallbacks();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void requery(@Nullable Bundle args) {
        super.requery(args);

        if (isResumed()) {
            getLoaderManager().restartLoader(BROWSE_LOADER_ID, getMutableArguments(), createLoaderCallbacks());
        }
    }

    @Nonnull
    protected String getParentNodeId() {
        NavigationItem item = NavigationItem.Companion.getNavigationItem(getMutableArguments());
        if(item == null) {
            throw new IllegalStateException("require navigation item");
        }
        String nodeId = item.getNodeId();
        if (nodeId == null) {
            throw new IllegalStateException("require nodeid");
        }
        return nodeId;
    }

    @Override
    @Nonnull
    protected NodeItemAdapter createListAdapter() {
        NodeItemAdapter adapter = new NodeItemAdapter(requireContext(), getThumbnailProcessor());
        return adapter;
    }

    @Override
    protected boolean onActionButtonClicked(StandardMenuItem item, View actionButton, int position) {
        return false;
    }

    @Override
    protected void onStandardItemClick(StandardMenuItem item, View view, int position) {
        clickHandler((NodeItem) item, this, getAdapter());
    }

    private class NodeLoaderCallbacks implements LoaderCallbacks<List<? extends Item>> {
        public NodeLoaderCallbacks() {
        }

        @Override
        @Nonnull
        public Loader<List<? extends Item>> onCreateLoader(int id, @Nullable Bundle args) {
            switch (id) {
                case BROWSE_LOADER_ID:
                    NodeItemLoader loader = new NodeItemLoader(getEffectivePlayerId(), getParentNodeId());
                    onLoaderDataReceived(null, true, false);
                    return loader;
                default:
                    throw new IllegalArgumentException("unexpected loader id " + id);
            }
        }

        @Override
        public void onLoadFinished(Loader<List<? extends Item>> loader, List<? extends Item> items) {
            switch (loader.getId()) {
                case BROWSE_LOADER_ID:
                    OSBrowseAdapter adapter = getAdapter();
                    adapter.setNotifyOnChange(false);
                    adapter.clear();
                    adapter.addAll(items);
                    adapter.notifyDataSetChanged();

                    setShowProgressIndicator(false);
                    setLoadProgress(1, 1);

                    onLoaderDataReceived(items, adapter.isEmpty(), true);
                    break;
                default:
                    throw new IllegalArgumentException("unexpected loader id " + loader.getId());
            }
        }

        @Override
        public void onLoaderReset(Loader<List<? extends Item>> loader) {
            // nothing
        }
    }

    static public void clickHandler(NodeItem item, BrowseNodeFragment fragment, OSBrowseAdapter adapter) {
        MenuElement elem = item.getMenuElement();

        if (elem.isANode()) {
            NavigationManager manager = fragment.getNavigationManager();
            Intent intent = manager.newBrowseNodeIntent(item.getItemTitle(), item.getId(), fragment.getFillPlayerId());

            fragment.populatePersistentExtras(intent);

            manager.startActivity(intent);

            fragment.requireActivity().overridePendingTransition(R.anim.in_from_right, android.R.anim.fade_out);
        } else {
            MenuAction action = MenuHelpers.getAction(elem, ActionNames.GO);
            fragment.execute(item.getText(), elem, action, null, false, null);

            // the execute may have modified the menu contents, so refresh the adapter
            adapter.notifyDataSetChanged();
        }
    }

    final private Object mEventReceiver = new Object() {

        @Subscribe
        public void whenCurrentConnectionStateChanges(ConnectionStateChangedEvent event) {
            requery(null);
        }

        @Subscribe
        public void whenActivePlayerChange(ActivePlayerChangedEvent event) {
            requery(null);
        }
    };
}
