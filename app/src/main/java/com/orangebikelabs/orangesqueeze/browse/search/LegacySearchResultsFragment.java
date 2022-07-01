/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import android.os.Bundle;
import android.view.View;

import com.fasterxml.jackson.databind.JsonNode;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequest;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.menu.MenuListAdapter;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class LegacySearchResultsFragment extends GlobalSearchResultsFragment {

    @Nonnull
    public static LegacySearchResultsFragment newInstance(String query) {
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);

        OSAssert.assertParcelable(args);

        LegacySearchResultsFragment fragment = new LegacySearchResultsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    @Nonnull
    protected BrowseRequest newRequest(@Nullable Bundle args) {
        String query = getQuery().or("");

        return new LegacySearchRequest(SBContextProvider.get().getPlayerId(), query);
    }

    @Override
    @Nonnull
    protected MenuListAdapter createListAdapter() {
        return new LegacySearchListAdapter(requireContext(), getThumbnailProcessor());
    }

    @Override
    protected void onStandardItemClick(StandardMenuItem item, View view, int position) {
        JsonNode record = item.getNode();
        if (record.has("contributor_id")) {
            String artistId = record.get("contributor_id").asText();
            NavigationItem navigationItem = NavigationItem.Companion.newBrowseArtistItem(artistId, item.toString());
            executeMenuBrowse(navigationItem);
        } else if (record.has("album_id")) {
            String albumId = record.get("album_id").asText();
            NavigationItem navigationItem = NavigationItem.Companion.newBrowseAlbumItem(albumId, item.toString());
            executeMenuBrowse(navigationItem);
        } else if (record.has("track_id")) {
            executeDefaultSelectionAction(view, item);
        } else {
            throw new IllegalArgumentException("Unsupported JSON record for search");
        }
    }

}
