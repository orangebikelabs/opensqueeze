/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.github.chrisbanes.photoview.PhotoView;
import com.google.common.util.concurrent.Futures;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.AutoSizeTextHelper;
import com.orangebikelabs.orangesqueeze.common.AbsFragmentResultReceiver;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.NavigationManager;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerNotFoundException;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus.RepeatMode;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus.ShuffleMode;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.common.ServerStatus.Transaction;
import com.orangebikelabs.orangesqueeze.databinding.NowplayingBinding;

import javax.annotation.Nullable;

/**
 * TODO detect mog, pandora, link to those pages?
 *
 * @author tbsandee@orangebikelabs.com
 */
public class NowPlayingFragment extends AbsNowPlayingFragment {
    public static final String TAG = NowPlayingFragment.class.getName();

    @Nullable
    private FutureResult mLastSeekCommand;

    @Nullable
    private ImageButton mShuffleButton;

    @Nullable
    private ImageButton mRepeatButton;

    private String[] mShuffleStrings;
    private String[] mRepeatStrings;

    private NowplayingBinding mBinding;

    private AutoSizeTextHelper mAutosizeTextHelper = new AutoSizeTextHelper();

    public NowPlayingFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mShuffleStrings = getResources().getStringArray(R.array.shuffle_strings);
        mRepeatStrings = getResources().getStringArray(R.array.repeat_strings);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = NowplayingBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mBinding = null;
    }

    @Nullable
    @Override
    protected View getSnackbarView() {
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBinding.info.artistText.setOnClickListener(mArtistClickedListener);
        mAutosizeTextHelper.applyAutoSize(mBinding.info.artistText, 2);

        mBinding.info.albumText.setOnClickListener(mAlbumClickedListener);
        mAutosizeTextHelper.applyAutoSize(mBinding.info.albumText, 2);

        mBinding.info.trackText.setOnClickListener(mTrackClickedListener);
        mAutosizeTextHelper.applyAutoSize(mBinding.info.trackText, 2);

        mBinding.progress.seekbar.setOnSeekBarChangeListener(mSeekBarListener);

        mShuffleButton = view.findViewById(R.id.shuffle_button);
        mRepeatButton = view.findViewById(R.id.repeat_button);

        // enable zooming for large artwork
        ImageSwitcher fullSwitcher = mFullArtworkSwitcher;
        if (fullSwitcher != null) {
            for (int i = 0; i < fullSwitcher.getChildCount(); i++) {
                PhotoView iv = (PhotoView) fullSwitcher.getChildAt(i);
                OSAssert.assertNotNull(iv, "PhotoView should be non-null");

                iv.setOnLongClickListener(v -> {
                    CurrentPlaylistItemCallback cb = (CurrentPlaylistItemCallback) getActivity();
                    if (cb == null) {
                        return false;
                    }

                    PlaylistItem current = cb.getCurrentPlaylistItem();
                    if (current == null) {
                        return false;
                    }
                    current.showContextMenu(NowPlayingFragment.this, mBinding.artwork.artworkLoadingProgress);
                    return true;
                });
            }
        }
    }

    @Override
    protected void updatePlayerUI(PlayerStatus status) {
        // ignore player updates while we're waiting for the seek to complete
        if (mLastSeekCommand != null && !mLastSeekCommand.isCommitted()) {
            return;
        }
        mLastSeekCommand = null;

        super.updatePlayerUI(status);

        // always set this to true, even when isSeekable() is false
        // many streams are seekable even if this returns false (Pandora, Deezer, etc)
//        mTrackPositionBar.setEnabled(status.canSeek());
//        if (status.getTotalTime() != 0) {
//            mTrackPositionBar.setVisibility(View.VISIBLE);
//        } else {
//            mTrackPositionBar.setVisibility(View.INVISIBLE);
//        }
        mBinding.progress.seekbar.setEnabled(true);

        boolean trackTransition = quickSetText(mTrackText, getTrackText(status));

        if (!trackTransition) {
            trackTransition = (mBinding.progress.seekbar.getMax() != (int) status.getTotalTime());
        }

        if (mShuffleButton != null) {
            int shuffleOrdinal = status.getShuffleMode().ordinal();
            mShuffleButton.setContentDescription(mShuffleStrings[shuffleOrdinal]);
        }

        if (mRepeatButton != null) {
            int repeatOrdinal = status.getRepeatMode().ordinal();
            mRepeatButton.setContentDescription(mRepeatStrings[repeatOrdinal]);
        }

        if (mInterimUpdateMode != InterimUpdateMode.ON || trackTransition) {
            final boolean estimate = false;
            // only update these from this thread during a track transition to
            // promote visual consistency
            setProgress(status, estimate);

            quickSetText(mElapsedText, getElapsedText(status, estimate));

            quickSetText(mDurationText, getDurationText(status, estimate));
        }
    }

    @Override
    protected void setProgress(PlayerStatus status, boolean estimate) {
        int elapsed = (int) (status.getElapsedTime(estimate));
        int total = (int) (status.getTotalTime());
        if (total != 0) {
            mBinding.progress.seekbar.setProgress(elapsed);
        } else {
            mBinding.progress.seekbar.setProgress(0);
        }
        mBinding.progress.seekbar.setMax(total);
    }

    @Override
    protected void onPlayerControlClicked(int id) {
        super.onPlayerControlClicked(id);

        try {
            final PlayerStatus playerStatus = mSbContext.getCheckedPlayerStatus();

            if (id == R.id.shuffle_button) {
                final ShuffleMode initialShuffleMode = playerStatus.getShuffleMode();
                FutureResult futureResult = mSbContext.sendPlayerCommand("playlist", "shuffle");
                Futures.addCallback(futureResult, new AbsFragmentResultReceiver<NowPlayingFragment>(this) {
                    @Override
                    public void onEventualSuccess(NowPlayingFragment fragment, SBResult result) {
                        int newVal = initialShuffleMode.ordinal() + 1;
                        if (newVal >= ShuffleMode.values().length) {
                            newVal = 0;
                        }

                        showSnackbar(mShuffleStrings[newVal], SnackbarLength.SHORT);

                        Transaction transaction = mSbContext.getServerStatus().newTransaction();
                        try {
                            PlayerStatus newStatus = playerStatus.withShuffleMode(ShuffleMode.values()[newVal]);
                            transaction.add(newStatus);
                            transaction.markSuccess();
                        } finally {
                            transaction.close();
                        }

                        updatePlayerUI();
                    }
                }, OSExecutors.getMainThreadExecutor());
            } else if (id == R.id.repeat_button) {
                final RepeatMode initialRepeatMode = playerStatus.getRepeatMode();
                FutureResult futureResult = mSbContext.sendPlayerCommand("playlist", "repeat");
                Futures.addCallback(futureResult, new AbsFragmentResultReceiver<NowPlayingFragment>(this) {

                    @Override
                    public void onEventualSuccess(NowPlayingFragment fragment, SBResult result) {
                        int newVal = initialRepeatMode.ordinal() + 1;
                        if (newVal >= RepeatMode.values().length) {
                            newVal = 0;
                        }
                        showSnackbar(mRepeatStrings[newVal], SnackbarLength.SHORT);

                        // this is a hack but good enough for now. it will be
                        // overwritten on the first status update
                        Transaction transaction = mSbContext.getServerStatus().newTransaction();
                        try {
                            PlayerStatus newStatus = playerStatus.withRepeatMode(RepeatMode.values()[newVal]);
                            transaction.add(newStatus);
                            transaction.markSuccess();
                        } finally {
                            transaction.close();
                        }
                        updatePlayerUI();
                    }
                }, OSExecutors.getMainThreadExecutor());
            }
        } catch (PlayerNotFoundException e) {
            // ignore
        }
    }

    /**
     * Support instance to handle dragging around the progressbar
     */
    final private OnSeekBarChangeListener mSeekBarListener = new OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mInterimUpdateMode = InterimUpdateMode.REENABLE;
            mLastSeekCommand = mSbContext.sendPlayerCommand("time", String.valueOf(seekBar.getProgress()));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mInterimUpdateMode = InterimUpdateMode.OFF;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            PlayerStatus status = mSbContext.getPlayerStatus();
            if (fromUser && mElapsedText != null && status != null) {
                PlayerStatus updated = status.withElapsedTime(progress);

                quickSetText(mElapsedText, getElapsedText(updated, false));
                quickSetText(mDurationText, getDurationText(updated, false));
            }
        }
    };

    final private OnClickListener mArtistClickedListener = v -> {
        try {
            PlayerStatus status = mSbContext.getCheckedPlayerStatus();

            String artistId = status.getArtistId().orNull();
            String artist = status.getDisplayArtist();

            if (artistId != null) {
                startBrowseArtist(artist, artistId);
            } else if (!artist.equals("")) {
                startSearchArtist(artist);
            }
        } catch (PlayerNotFoundException e) {
            OSLog.i(e.getMessage(), e);
        }
    };

    final private OnClickListener mAlbumClickedListener = v -> {
        try {
            PlayerStatus status = mSbContext.getCheckedPlayerStatus();
            String albumId = status.getAlbumId().orNull();
            String album = status.getAlbum();

            if (albumId != null) {
                startBrowseAlbum(album, albumId);
            } else if (!album.equals("")) {
                startSearchAlbum(album);
            }
        } catch (PlayerNotFoundException e) {
            OSLog.i(e.getMessage(), e);
        }
    };

    final private OnClickListener mTrackClickedListener = v -> {
        try {
            PlayerStatus status = mSbContext.getCheckedPlayerStatus();
            String track = status.getTrack();

            if (!track.equals("")) {
                startSearchTrack(track);
            }
        } catch (PlayerNotFoundException e) {
            OSLog.i(e.getMessage(), e);
        }
    };

    private void startSearchTrack(String trackName) {
        NavigationManager manager = getNavigationManager();
        Intent intent = manager.createSearchTrackIntent(trackName);
        manager.startActivity(intent);
    }

    private void startSearchArtist(String artistName) {
        NavigationManager manager = getNavigationManager();
        Intent intent = manager.createSearchArtistIntent(artistName);
        manager.startActivity(intent);
    }

    private void startSearchAlbum(String albumName) {
        NavigationManager manager = getNavigationManager();
        Intent intent = manager.createSearchAlbumIntent(albumName);
        manager.startActivity(intent);
    }

    private void startBrowseAlbum(String title, String albumId) {
        NavigationManager manager = getNavigationManager();
        NavigationItem item = NavigationItem.Companion.newBrowseAlbumItem(albumId, title);
        Intent intent = manager.newBrowseRequestIntent(item);
        manager.startActivity(intent);
    }

    private void startBrowseArtist(String title, String artistId) {
        NavigationManager manager = getNavigationManager();
        NavigationItem item = NavigationItem.Companion.newBrowseArtistItem(artistId, title);
        Intent intent = manager.newBrowseRequestIntent(item);
        manager.startActivity(intent);
    }
}
