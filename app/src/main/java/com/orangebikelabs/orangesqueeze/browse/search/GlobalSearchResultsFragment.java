/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;
import androidx.appcompat.widget.SearchView;
import java.util.Optional;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.actions.AbsAction;
import com.orangebikelabs.orangesqueeze.actions.ActionDialogBuilder;
import com.orangebikelabs.orangesqueeze.browse.BrowseRequestFragment;
import com.orangebikelabs.orangesqueeze.browse.OSBrowseAdapter;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequest;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequestData;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.LoopingRequestLoader;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.VersionIdentifier;
import com.orangebikelabs.orangesqueeze.menu.MenuListAdapter;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class GlobalSearchResultsFragment extends BrowseRequestFragment {

    public static final String ARG_QUERY = "query";

    @Nonnull
    public static GlobalSearchResultsFragment newInstance(String query) {

        boolean useLegacySearch = SBContextProvider.get().getServerStatus().getVersion().compareTo(new VersionIdentifier("7.5.0")) < 0;
        if (useLegacySearch) {
            return LegacySearchResultsFragment.newInstance(query);
        }

        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);

        OSAssert.assertParcelable(args);

        GlobalSearchResultsFragment fragment = new GlobalSearchResultsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public GlobalSearchResultsFragment() {
    }

    @Override
    public void onCreate(@androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requireActivity().addMenuProvider(mMenuProvider, this, Lifecycle.State.RESUMED);
    }

    @Override
    @Nonnull
    protected BrowseRequest newRequest(@Nullable Bundle args) {
        String query = getQuery().orElse("");
        BrowseRequest retval = new GlobalSearchRequest(SBContextProvider.get().getPlayerId(), query);
        return retval;
    }

    @Override
    @Nonnull
    protected LoaderCallbacks<BrowseRequestData> createLoaderCallbacks() {
        return new SearchLoaderCallbacks();
    }

    @Override
    @Nonnull
    protected MenuListAdapter createListAdapter() {
        return new GlobalSearchListAdapter(requireContext(), getThumbnailProcessor());
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getQuery().ifPresent(val -> requery(null));
    }

    @Nonnull
    protected Optional<String> getQuery() {
        Bundle args = getMutableArguments();
        return Optional.ofNullable(args.getString(ARG_QUERY));
    }

    protected boolean showExpandableSearchHeaderMenu(View v, ExpandableSearchHeaderItem item) {
        boolean handled = false;
        List<AbsAction<ExpandableSearchHeaderItem>> actionList = new ArrayList<>();

        actionList.add(new ExpandSearchNodeAction(requireContext()));
        actionList.add(new DontExpandSearchNodeAction(requireContext()));

        ActionDialogBuilder<ExpandableSearchHeaderItem> builder = ActionDialogBuilder.newInstance(this, v);
        builder.setAvailableActions(actionList);

        if (builder.applies(item)) {
            String title = getString(R.string.actionmenu_title_html, item.toString());
            builder.setTitle(HtmlCompat.fromHtml(title, 0));
            builder.create().show();
            handled = true;
        }
        return handled;
    }

    @Override
    protected boolean onActionButtonClicked(StandardMenuItem item, View actionButton, int position) {
        if (item instanceof ExpandableSearchHeaderItem) {
            return showExpandableSearchHeaderMenu(actionButton, (ExpandableSearchHeaderItem) item);
        } else {
            return super.onActionButtonClicked(item, actionButton, position);
        }
    }

    final private MenuProvider mMenuProvider = new MenuProvider() {
        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            MenuItem searchItem = menu.findItem(R.id.menu_search);
            if (searchItem != null) {
                SearchView searchView = (SearchView) searchItem.getActionView();
                OSAssert.assertNotNull(searchView, "searchView should not be null");

                searchView.setSubmitButtonEnabled(true);
                searchView.setQueryHint(getString(R.string.search_hint));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        Bundle newArgs = new Bundle();
                        newArgs.putString(GlobalSearchResultsFragment.ARG_QUERY, query);

                        OSAssert.assertParcelable(newArgs);
                        requery(newArgs);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        return false;
                    }
                });

                getQuery().ifPresent(query -> searchView.setQuery(query, false));
            }
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            return false;
        }
    };

    class SearchLoaderCallbacks implements LoaderCallbacks<BrowseRequestData> {
        @Override
        @Nonnull
        public Loader<BrowseRequestData> onCreateLoader(int id, @Nullable Bundle args) {
            switch (id) {
                case BROWSE_LOADER_ID -> {
                    BrowseRequest request = newRequest(args);
                    return new LoopingRequestLoader<>(request);
                }
                default -> throw new IllegalArgumentException();
            }
        }

        @Override
        public void onLoadFinished(Loader<BrowseRequestData> loader, BrowseRequestData requestData) {
            OSBrowseAdapter adapter = getAdapter();

            adapter.setNotifyOnChange(false);
            adapter.clear();
            adapter.addAll(requestData.getItemList());
            adapter.notifyDataSetChanged();

            setShowProgressIndicator(!requestData.isComplete());
            setLoadProgress(requestData.getPosition(), requestData.getTotalCount());
        }

        @Override
        public void onLoaderReset(Loader<BrowseRequestData> loader) {
            // nothing
        }
    }
}
