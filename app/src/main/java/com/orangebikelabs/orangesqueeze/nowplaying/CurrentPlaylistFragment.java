/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nhaarman.listviewanimations.itemmanipulation.DynamicListView;
import com.nhaarman.listviewanimations.itemmanipulation.dragdrop.OnItemMovedListener;
import com.nhaarman.listviewanimations.itemmanipulation.dragdrop.TouchViewDraggableManager;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.MenuTools;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.ScrollingState;
import com.orangebikelabs.orangesqueeze.common.event.ActivePlayerChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState;
import com.orangebikelabs.orangesqueeze.common.event.ItemActionButtonClickEvent;
import com.orangebikelabs.orangesqueeze.menu.AbsMenuFragment;
import com.squareup.otto.Subscribe;

import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CurrentPlaylistFragment extends AbsMenuFragment {

    @Nonnull
    public static CurrentPlaylistFragment newInstance() {
        return new CurrentPlaylistFragment();
    }

    public static final String TAG = CurrentPlaylistFragment.class.getName();
    public static final int CONFIRMATION_ID_CLEARPLAYLIST = 1;

    final private Handler mHandler = new Handler(Looper.getMainLooper());
    /**
     * the duration we will wait before resuming automatic selection and scroll positioning of the playlist view
     */
    private static final long AUTOSCROLL_DELAY = 20000;
    private static final int PLAYLIST_LOADER = 0;

    protected int mScrollState;
    protected long mNextAutoScroll;
    protected long mLastProgrammaticScroll;
    private boolean mNeedsInitialScroll;

    @Nullable
    private FutureResult mPlaylistChangeCommand;

    protected DynamicListView mPlaylistList;
    protected ThumbnailProcessor mThumbnailProcessor;
    protected PlaylistListAdapter mAdapter;

    @Nullable
    private PlaylistListRequestData mReadyPlaylistData;

    private String mLastTrackHash;

    public CurrentPlaylistFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // enable menu handling when the fragment is resumed
        requireActivity().addMenuProvider(mMenuProvider, this, Lifecycle.State.RESUMED);

        mThumbnailProcessor = new ThumbnailProcessor(requireContext());

        mBus.register(mEventReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBus.unregister(mEventReceiver);
        mThumbnailProcessor = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.nowplaying_playlist, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAdapter = new PlaylistListAdapter(requireContext(), mThumbnailProcessor);
        mPlaylistList = view.findViewById(R.id.playlist_list);
        mPlaylistList.setAdapter(mAdapter);

        // track if the user is scrolling the view so that we can set the
        // timeout for showing the active playlist item
        ScrollingState.monitorScrolling(mPlaylistList, new OnScrollListener() {
            boolean skipUntilIdle;

            @Override
            public void onScroll(AbsListView lv, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // no implementation
            }

            @Override
            public void onScrollStateChanged(AbsListView lv, int scrollState) {
                mScrollState = scrollState;

                if (skipUntilIdle) {
                    if (scrollState == SCROLL_STATE_IDLE) {
                        skipUntilIdle = false;

                        deferNextAutoScroll();
                    }
                    return;
                }

                // ignore events we generated
                if (scrollState != SCROLL_STATE_IDLE) {
                    long diff = SystemClock.uptimeMillis() - mLastProgrammaticScroll;
                    if (diff < 1000) {
                        skipUntilIdle = true;
                        return;
                    }
                }

                // any change in scroll state bumps up the next auto-scroll
                deferNextAutoScroll();
            }
        });

        // select the current playlist item
        mPlaylistList.setOnItemClickListener((parent, clickView, position, id) -> control_PlayIndex(position));
        mPlaylistList.enableDragAndDrop();
        mPlaylistList.setDraggableManager(new TouchViewDraggableManager(R.id.icon));
        mPlaylistList.setOnItemMovedListener(mItemMovedListener);
        mPlaylistList.enableSwipeToDismiss((viewGroup, reverseSortedPositions) -> internalRemoveItems(reverseSortedPositions));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        LoaderManager.getInstance(this).initLoader(PLAYLIST_LOADER, null, mLoaderCallbacks);
    }

    @Override
    public void onStart() {
        super.onStart();

        mThumbnailProcessor.onStart();
        startAutoScroll();
    }

    @Override
    public void onStop() {
        super.onStop();

        mThumbnailProcessor.onStop();
        stopAutoScroll();
    }

    @Nullable
    @Override
    protected View getSnackbarView() {
        return mPlaylistList;
    }

    /**
     * called if the user is mussing with the playlist view to disable the automatic scrolling of the playlist view
     */
    private void deferNextAutoScroll() {
        mHandler.removeCallbacks(mAutoScrollRunnable);

        mNextAutoScroll = SystemClock.uptimeMillis() + AUTOSCROLL_DELAY;
        mHandler.postDelayed(mAutoScrollRunnable, AUTOSCROLL_DELAY);
    }

    private void startAutoScroll() {
        mNeedsInitialScroll = true;
    }

    private void stopAutoScroll() {
        mHandler.removeCallbacks(mAutoScrollRunnable);
    }

    final private Runnable mAutoScrollRunnable = this::performAutoScroll;

    private void performAutoScroll() {
        deferNextAutoScroll();

        mHandler.post(this::internal_AutoScrollPlaylistIndex);
    }

    private void internal_AutoScrollPlaylistIndex() {
        if (!SBPreferences.get().shouldAutoscrollToCurrentItem()) return;

        boolean scrolling = ScrollingState.isScrolling() || mScrollState != OnScrollListener.SCROLL_STATE_IDLE;

        if (!scrolling) {
            PlayerStatus status = mSbContext.getPlayerStatus();
            if (status != null) {
                mLastProgrammaticScroll = SystemClock.uptimeMillis();

                int ndx = status.getPlaylistIndex();

                if (ndx >= 0 && ndx < mPlaylistList.getCount()) {
                    if (ndx >= mPlaylistList.getFirstVisiblePosition() && ndx < mPlaylistList.getLastVisiblePosition()) {
                        // do nothing
                    } else {
                        mPlaylistList.smoothScrollToPositionFromTop(ndx, mPlaylistList.getHeight() / 3);
                    }
                }
            }
        } else {
            deferNextAutoScroll();
        }
    }

    private void updateCurrentPlaylistItem() {
        // update the current item
        CurrentPlaylistItemCallback cb = (CurrentPlaylistItemCallback) getActivity();
        if (cb == null) {
            return;
        }

        int index = mAdapter.getPlaylistIndex();

        PlaylistItem item = null;
        if (index >= 0 && index < mAdapter.getCount()) {
            item = (PlaylistItem) mAdapter.getItem(index);
        }
        cb.setCurrentPlaylistItem(item);
    }

    /**
     * local event receiver
     */
    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenCurrentPlayerStatusChanges(CurrentPlayerState event) {

            PlayerStatus status = event.getPlayerStatus();
            if (status == null) {
                // no player active
                return;
            }

            // FIXME are we in a touch event dragging?
            if ((mPlaylistChangeCommand != null && !mPlaylistChangeCommand.isCommitted())) {
                // wait for operation to complete before checking things
                return;
            }

            boolean restartLoader = false;
            String adapterTimestamp = mAdapter.getPlaylistTimestamp().orElse(null);

            if (adapterTimestamp != null) {
                // we're viewing a playlist....
                String newPlaylistTimestamp = status.getPlaylistTimestamp();
                int newPlaylistIndex = status.getPlaylistIndex();

                // is it the same?
                if (!adapterTimestamp.equals(newPlaylistTimestamp)) {
                    // no, reload it
                    restartLoader = true;
                } else if (mAdapter.getPlaylistIndex() != newPlaylistIndex) {
                    // yes, maybe update the index
                    mAdapter.setPlaylistIndex(newPlaylistIndex);
                    mAdapter.notifyDataSetChanged();
                    updateCurrentPlaylistItem();
                } else if (!status.getTrackHash().equals(mLastTrackHash)) {
                    // remote metadata, just refresh the playlist
                    mLastTrackHash = status.getTrackHash();
                    restartLoader = true;
                }
            }

            if (restartLoader) {
                LoaderManager.getInstance(CurrentPlaylistFragment.this).restartLoader(PLAYLIST_LOADER, null, mLoaderCallbacks);
            }
            mPlaylistChangeCommand = null;
        }

        @Subscribe
        public void whenActivePlayerChanges(ActivePlayerChangedEvent event) {
            // force immediate scroll to current player
            performAutoScroll();
        }

        @Subscribe
        public void whenItemActionButtonClicked(ItemActionButtonClickEvent event) {
            if (event.appliesTo(CurrentPlaylistFragment.this)) {
                PlaylistItem pi = (PlaylistItem) event.getItem();
                pi.showContextMenu(CurrentPlaylistFragment.this, event.getActionButtonView());
            }
            deferNextAutoScroll();
        }
    };

    final private LoaderCallbacks<PlaylistListRequestData> mLoaderCallbacks = new LoaderCallbacks<>() {

        @Override
        @Nonnull
        public Loader<PlaylistListRequestData> onCreateLoader(int id, @Nullable Bundle args) {
            switch (id) {
                case PLAYLIST_LOADER:
                    return new CurrentPlaylistLoader();
                default:
                    throw new IllegalArgumentException("unexpected loader id " + id);
            }
        }

        @SuppressLint("NewApi")
        @Override
        public void onLoadFinished(Loader<PlaylistListRequestData> loader, PlaylistListRequestData requestData) {
            switch (loader.getId()) {
                case PLAYLIST_LOADER: {
                    mReadyPlaylistData = requestData;
                    updatePlaylist();
                    break;
                }
                default:
                    throw new IllegalArgumentException("unexpected loader id " + loader.getId());
            }
        }

        @Override
        public void onLoaderReset(Loader<PlaylistListRequestData> loader) {
            // nothing
        }

    };

    private void updatePlaylist() {
        if (mReadyPlaylistData == null) {
            return;
        }
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        mAdapter.addAll(mReadyPlaylistData.getItemList());
        mAdapter.setPlaylistTimestamp(mReadyPlaylistData.getPlaylistTimestamp());
        mAdapter.setPlaylistIndex(mReadyPlaylistData.getPlaylistIndex());
        mAdapter.notifyDataSetChanged();

        if (mNeedsInitialScroll) {
            mNeedsInitialScroll = false;
            performAutoScroll();
        }

        updateCurrentPlaylistItem();
        mReadyPlaylistData = null;
    }

    public void control_PlayIndex(int position) {
        // try to update the adapter immediately for visual consistency
        mAdapter.setPlaylistIndex(position);
        updateCurrentPlaylistItem();
        mPlaylistChangeCommand = mSbContext.sendPlayerCommand(getEffectivePlayerId(), Arrays.asList("playlist", "index", position));

        deferNextAutoScroll();
    }

    /**
     * clear the current playlist, called by menu item
     */
    public void clearPlaylist() {
        mSbContext.sendPlayerCommand(getEffectivePlayerId(), "playlist", "clear");
        showSnackbar(getString(R.string.cleared_playlist), SnackbarLength.SHORT);
    }

    private void internalRemoveItems(int[] reverseSortedPositions) {
        for (int pos : reverseSortedPositions) {
            // we might be processing a playlist refresh command, in which case the adapter will be "off"
            if (pos >= 0 && pos < mAdapter.getCount()) {
                mAdapter.remove(pos);
            }
            mPlaylistChangeCommand = mSbContext.sendPlayerCommand(getEffectivePlayerId(), Arrays.asList("playlist", "delete", pos));
        }

        deferNextAutoScroll();
    }

    final private MenuProvider mMenuProvider = new MenuProvider() {
        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.currentplaylist, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menu_nowplaying_clearplaylist) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
                builder.setTitle(R.string.confirmation_title)
                        .setMessage(R.string.clearplaylist_action_confirmation)
                        .setPositiveButton(R.string.ok, (dlg, which) -> clearPlaylist())
                        .setNegativeButton(R.string.cancel, (dlg, which) -> {
                        })
                        .show();
                return true;
            } else if (menuItem.getItemId() == R.id.menu_nowplaying_saveplaylist) {
                PlayerId playerId = getEffectivePlayerId();
                if (playerId != null) {
                    SavePlaylistDialog.newInstance(CurrentPlaylistFragment.this, mPlaylistList, playerId)
                            .show();
                }
                return true;
            } else if (menuItem.getItemId() == R.id.menu_nowplaying_snaptocurrentitem) {
                SBPreferences.get().setShouldAutoscrollToCurrentItem(!menuItem.isChecked());
                if (SBPreferences.get().shouldAutoscrollToCurrentItem()) {
                    performAutoScroll();
                }
                return true;
            }
            return false;
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            MenuTools.setVisible(menu, R.id.menu_nowplaying_clearplaylist, true);
            MenuTools.setVisible(menu, R.id.menu_nowplaying_saveplaylist, true);

            MenuItem item = menu.findItem(R.id.menu_nowplaying_snaptocurrentitem);
            if (item != null) {
                item.setChecked(SBPreferences.get().shouldAutoscrollToCurrentItem());
            }
        }
    };

    final private OnItemMovedListener mItemMovedListener = (positionOne, positionTwo) -> {
        mPlaylistChangeCommand = mSbContext.sendPlayerCommand(getEffectivePlayerId(), Arrays.asList("playlist", "move", positionOne, positionTwo));
        deferNextAutoScroll();
    };
}
