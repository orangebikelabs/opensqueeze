/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequest;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequestData;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.browse.common.LoadingItem;
import com.orangebikelabs.orangesqueeze.browse.common.SeparatorItem;
import com.orangebikelabs.orangesqueeze.common.MenuTools;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.LoopingRequestLoader;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.menu.MenuListAdapter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fragment used for request-driven browsing, usually based on a LoopedRequest.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class BrowseRequestFragment extends AbsBrowseFragment<MenuListAdapter, BrowseRequestData> {

    protected boolean mRefreshCache;

    @Nonnull
    static public BrowseRequestFragment newInstance(NavigationItem item) {
        BrowseRequestFragment retval = new BrowseRequestFragment();
        Bundle args = new Bundle();
        NavigationItem.Companion.putNavigationItem(args, item);
        retval.setArguments(args);
        return retval;
    }

    public BrowseRequestFragment() {
        // intentionally blank
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRefreshCache = false;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(Menu menu, MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.browse, menu);
            }

            @Override
            public boolean onMenuItemSelected(MenuItem menuItem) {
                boolean handled = false;
                if (menuItem.getItemId() == R.id.menu_browse_refresh) {
                    requery(null);
                    handled = true;
                }
                return handled;
            }

            @Override
            public void onPrepareMenu(Menu menu) {
                MenuTools.setVisible(menu, R.id.menu_browse_refresh, true);
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    @Nonnull
    protected MenuListAdapter createListAdapter() {
        /*			if (mSortBy != null) {
                        request.removeParameter("sort");
						switch (mSortBy) {
						case ALBUM:
							request.setParameter("orderBy", "album");
							break;
						case YEAR_ASC:
							// this is the default
							break;
						case YEAR_DESC:
							mManualReverse = true;
							break;
						case ARTISTYEARALBUM:
							request.removeParameter("sort");
							request.setParameter("orderBy", "yearalbum");
							break;
						}
					}
		*/
        return new MenuListAdapter(requireContext(), getThumbnailProcessor());
    }

    @Override
    public void requery(@Nullable Bundle args) {
        super.requery(args);

        mRefreshCache = true;

        if (isResumed()) {
            LoaderManager.getInstance(this).restartLoader(BROWSE_LOADER_ID, getMutableArguments(), createLoaderCallbacks());
        }
    }

    @Override
    @Nonnull
    protected BrowseRequest newRequest(Bundle args) {
        BrowseRequest request = super.newRequest(args);
        request.setShouldRefreshCache(mRefreshCache);
        return request;
    }

    /**
     * subclasses can override this to change the loader
     */
    @Override
    @Nonnull
    protected LoaderCallbacks<BrowseRequestData> createLoaderCallbacks() {
        return new BrowseLoaderCallbacks();
    }

    class BrowseLoaderCallbacks implements LoaderCallbacks<BrowseRequestData> {

        @Override
        @Nonnull
        public Loader<BrowseRequestData> onCreateLoader(int id, @Nullable Bundle args) {
            OSAssert.assertNotNull(args, "args shouldn't be null");

            switch (id) {
                case BROWSE_LOADER_ID:
                    BrowseRequest request = newRequest(args);
                    onLoaderDataReceived(new BrowseRequestData(request), true, false);
                    return new LoopingRequestLoader<>(request);
                default:
                    throw new IllegalArgumentException("unexpected loader id " + id);
            }
        }

        @SuppressLint("NewApi")
        @Override
        public void onLoadFinished(Loader<BrowseRequestData> loader, BrowseRequestData requestData) {
            switch (loader.getId()) {
                case BROWSE_LOADER_ID:
                    // must be a BaseBrowseAdapter instance for loaders
                    OSBrowseAdapter adapter = getAdapter();
                    adapter.setNotifyOnChange(false);
                    adapter.clear();
                    if (getBrowseStyle() != BrowseStyle.LIST) {
                        for (Item item : requestData.getItemList()) {
                            if (item instanceof SeparatorItem) {
                                // skip separators
                                continue;
                            }
                            adapter.add(item);
                        }
                    } else {
                        adapter.addAll(requestData.getItemList());
                    }

                    adapter.setSorted(requestData.isSorted());
                    setShowProgressIndicator(!requestData.isComplete());
                    setLoadProgress(requestData.getPosition(), requestData.getTotalCount());

                    if (!requestData.isComplete() && adapter.getCount() > 0) {
                        adapter.add(new LoadingItem(getString(R.string.loading_text)));
                    }

                    onLoaderDataReceived(requestData, adapter.isEmpty(), requestData.isComplete());

                    adapter.notifyDataSetChanged();
                    break;
                default:
                    throw new IllegalArgumentException("unexpected loader id " + loader.getId());
            }
        }

        @Override
        public void onLoaderReset(Loader<BrowseRequestData> loader) {
            // nothing
        }
    }

    /**
     * called when a browse request signals completion
     */
    @Override
    protected void onLoaderDataReceived(@Nullable BrowseRequestData requestData, boolean empty, boolean complete) {
        super.onLoaderDataReceived(requestData, empty, complete);

        if (complete && requestData != null) {
            mRefreshCache = false;
            try {
                FutureResult futureResult = requestData.getLastResult();
                if (futureResult != null) {
                    SBResult result = futureResult.checkedGet();
                    JsonNode json = result.getJsonResult();
                    OSAssert.assertNotNull(json, "JSON result should never be null after isSuccessful() returns true");

                    if (getAdapter().isEmpty()) {
                        View view = getView();
                        OSAssert.assertNotNull(view, "view can't be null");

                        if (json.has("artworkId")) {
                            ViewStub stub = view.findViewById(R.id.browseimage_stub);
                            if (stub != null) {
                                stub.inflate();
                            }

                            String artworkId = json.get("artworkId").asText();

                            setVisibleBrowseView(R.id.artwork);
                            ImageView artworkView = view.findViewById(R.id.artwork);
                            if (artworkView != null) {
                                getThumbnailProcessor().addArtworkJob(artworkView, artworkId, ArtworkType.ALBUM_FULL, ScaleType.FIT_START);
                            }
                        } else if (json.has("window")) {
                            ViewStub stub = view.findViewById(R.id.browsetext_stub);
                            if (stub != null) {
                                stub.inflate();
                            }

                            JsonNode textArea = json.get("window").get("textarea");
                            if (textArea != null) {
                                setVisibleBrowseView(R.id.textarea_scroll);
                                TextView textAreaView = view.findViewById(R.id.textarea);
                                if (textAreaView != null) {
                                    String text = textArea.asText().replaceAll("\\\\n", "\n");

                                    textAreaView.setText(text);
                                }
                            }
                        }
                    }
                }
            } catch (SBRequestException e) {
                // only show this error if we're connected
                OSLog.e(e.getMessage(), e);
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
                builder.setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.network_error)
                        .setMessage(e.getMessage())
                        .show();
            } catch (InterruptedException e) {
                // ignore, request was interrupted
            }
        }
    }
}
