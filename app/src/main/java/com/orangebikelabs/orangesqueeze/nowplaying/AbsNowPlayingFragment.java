/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.SparseIntArray;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Objects;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.artwork.Artwork;
import com.orangebikelabs.orangesqueeze.common.AbsFragmentResultReceiver;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.PlayerCommands;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerNotFoundException;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus.ButtonStatus;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus.PlayerButton;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState;
import com.orangebikelabs.orangesqueeze.menu.AbsMenuFragment;
import com.orangebikelabs.orangesqueeze.ui.VolumeFragment;
import com.squareup.otto.Subscribe;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import androidx.annotation.DrawableRes;
import androidx.core.widget.ContentLoadingProgressBar;

/**
 * @author tbsandee@orangebikelabs.com
 */
abstract public class AbsNowPlayingFragment extends AbsMenuFragment {
    final static protected Handler sHandler = new Handler(Looper.getMainLooper());

    // begin block of UI items
    @Nullable
    protected TextView mAlbumText;

    @Nullable
    protected TextView mArtistText;

    @Nullable
    protected TextView mTrackText;

    @Nullable
    protected TextView mElapsedText;

    @Nullable
    protected TextView mDurationText;

    @Nullable
    protected ContentLoadingProgressBar mArtworkLoadingProgressBar;

    // some activities provide full artwork
    @Nullable
    protected ImageSwitcher mFullArtworkSwitcher;

    // others provide thumbnail
    @Nullable
    protected ImageSwitcher mThumbnailArtworkSwitcher;

    /**
     * complete list of artwork imageswitchers
     */
    @Nonnull
    protected List<ImageSwitcher> mArtworkList = Collections.emptyList();

    @Nonnull
    final protected Set<View> mControls = new LinkedHashSet<>();

    /**
     * common string builder object to use from the main thread only
     */
    final protected StringBuilder mMainThreadStringBuilder = new StringBuilder();


    protected enum InterimUpdateMode {
        /**
         * no interim updates and cancel
         */
        CANCEL,
        /**
         * no interim updates but continue checking
         */
        OFF,
        /**
         * yes interim updates
         */
        ON,
        /**
         * interim updates enabled once an update is broadcast
         */
        REENABLE
    }

    /**
     * used to track status of interim update mode
     */
    @Nullable
    protected InterimUpdateMode mInterimUpdateMode;

    protected int mFullSizeArtworkWidth;

    protected int mTinyArtworkWidth;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mInterimUpdateMode = InterimUpdateMode.CANCEL;

        mBus.register(mEventReceiver);

        mFullSizeArtworkWidth = Artwork.getFullSizeArtworkWidth(requireContext());

        TypedArray ta = requireContext().obtainStyledAttributes(new int[]{R.attr.tinyNowPlayingArtworkSize});
        mTinyArtworkWidth = ta.getDimensionPixelSize(0, -1);
        ta.recycle();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBus.unregister(mEventReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();

        // release memory of full-size images when the fragment stops
        for (ImageSwitcher switcher : mArtworkList) {
            internalArtworkSetNone(switcher);

            // reset the switcher so if it redisplays, we won't animate
            switcher.reset();
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAlbumText = view.findViewById(R.id.album_text);
        mArtistText = view.findViewById(R.id.artist_text);
        mTrackText = view.findViewById(R.id.track_text);
        mElapsedText = view.findViewById(R.id.current_time);
        mDurationText = view.findViewById(R.id.total_time);

        mArtworkLoadingProgressBar = view.findViewById(R.id.artwork_loading_progress);
        mFullArtworkSwitcher = view.findViewById(R.id.artwork_full);
        mThumbnailArtworkSwitcher = view.findViewById(R.id.artwork_thumb);

        mArtworkList = new ArrayList<>();

        if (mFullArtworkSwitcher != null) {
            mArtworkList.add(mFullArtworkSwitcher);
        }
        if (mThumbnailArtworkSwitcher != null) {
            mArtworkList.add(mThumbnailArtworkSwitcher);
        }
        for (ImageSwitcher artworkSwitcher : mArtworkList) {
            artworkSwitcher.setImageResource(R.drawable.artwork_loading);
        }

        processControls(null, view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mInterimUpdateMode = InterimUpdateMode.CANCEL;

        mArtworkList.clear();
        mControls.clear();
    }

    // iterate through nested viewgroups to find all buttons, imageviews and set
    // the click listeners for them all
    protected void processControls(@Nullable View parent, View control) {
        if (control instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) control;
            for (int i = 0; i < group.getChildCount(); i++) {
                processControls(control, group.getChildAt(i));
            }
        } else {
            Object tag = control.getTag();
            if (Objects.equal(tag, "control")) {
                control.setOnClickListener(mControlClickListener);
                control.setOnLongClickListener(mControlLongClickListener);
                mControls.add(control);
            }
        }
    }

    /**
     * because Otto doesn't dispatch to subclasses for non-final fragments and activities we need an object here
     */
    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenCurrentPlayerStatusChanges(CurrentPlayerState event) {
            PlayerStatus status = event.getPlayerStatus();
            if (status == null) {
                // no player active
                return;
            }
            updatePlayerUI(status);
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        mInterimUpdateMode = InterimUpdateMode.ON;
        updatePlayerUI();

        InterrimRunnable runnable = new InterrimRunnable(this);
        runnable.run();
    }

    @Override
    public void onPause() {
        mInterimUpdateMode = InterimUpdateMode.CANCEL;

        super.onPause();
    }

    protected String getElapsedText(PlayerStatus status, boolean estimate) {
        int elapsed = (int) status.getElapsedTime(estimate);
        return DateUtils.formatElapsedTime(elapsed);
    }

    protected String getDurationText(PlayerStatus status, boolean estimate) {
        int elapsed = (int) status.getElapsedTime(estimate);
        int total = (int) status.getTotalTime();

        mMainThreadStringBuilder.setLength(0);
        if (total != 0) {
            int remaining = total - elapsed;
            mMainThreadStringBuilder.append("-");
            mMainThreadStringBuilder.append(DateUtils.formatElapsedTime(remaining));
            mMainThreadStringBuilder.append("  ");
        }
        return mMainThreadStringBuilder.toString();
    }

    protected String getTrackText(PlayerStatus status) {
        mMainThreadStringBuilder.setLength(0);
        String trackNumber = status.getTrackNumber().orNull();
        if (trackNumber != null) {
            mMainThreadStringBuilder.append(trackNumber);
            mMainThreadStringBuilder.append(". ");
        }
        mMainThreadStringBuilder.append(status.getTrack());
        return mMainThreadStringBuilder.toString();
    }

    /**
     * this is run periodically to update the elapsed time display every second. It is only executed if the interimUpdateMode is set to ON.
     * It is run from the main handler thread.
     */
    protected void performInterimUpdate() {
        try {
            PlayerStatus status = mSbContext.getCheckedPlayerStatus();

            final boolean estimate = true;

            setProgress(status, estimate);

            // these text fields are missing on the tiny view, don't bother calculating text fields on each update
            if (mElapsedText != null) {
                quickSetText(mElapsedText, getElapsedText(status, estimate));
            }
            if (mDurationText != null) {
                quickSetText(mDurationText, getDurationText(status, estimate));
            }
        } catch (PlayerNotFoundException e) {
            // ignore
        }
    }

    protected void updatePlayerUI() {
        if (!isAdded()) {
            return;
        }

        try {
            PlayerStatus status = mSbContext.getCheckedPlayerStatus();
            updatePlayerUI(status);
        } catch (PlayerNotFoundException e) {
            // ignore
        }
    }

    protected String getAlbumText(PlayerStatus status) {
        mMainThreadStringBuilder.setLength(0);
        mMainThreadStringBuilder.append(status.getAlbum());
        String year = status.getYear().orNull();
        if (year != null) {
            mMainThreadStringBuilder.append(" (");
            mMainThreadStringBuilder.append(year);
            mMainThreadStringBuilder.append(")");
        }
        return mMainThreadStringBuilder.toString();
    }

    /**
     * called when we receive an intent that we should update the player UI
     */
    protected void updatePlayerUI(PlayerStatus status) {
        // once first update arrives, reenable periodic updates
        if (mInterimUpdateMode == InterimUpdateMode.REENABLE) {
            mInterimUpdateMode = InterimUpdateMode.ON;
        }

        SparseIntArray visibilities = new SparseIntArray();
        PlayerControlStates.putViewVisibilities(status, visibilities);

        postprocessVisibilities(status, visibilities);

        for (View control : mControls) {
            int keyIndex = visibilities.indexOfKey(control.getId());
            int visibility = View.GONE;
            if (keyIndex >= 0) {
                visibility = visibilities.valueAt(keyIndex);
            }
            control.setVisibility(visibility);

            // optionally include the image level
            int imageLevel = PlayerControlStates.getCurrentViewImageLevel(status, control.getId());
            if (imageLevel >= 0) {
                ImageView iv = (ImageView) control;
                iv.setImageLevel(imageLevel);
            }

            if (control.getId() == R.id.volume_button) {
                // TODO change image if volume is locked?
                //				control.setEnabled(!status.isVolumeLocked());
            }
        }

        quickSetText(mAlbumText, getAlbumText(status));
        quickSetText(mArtistText, status.getDisplayArtist());

        updateArtworkUi(status);
    }

    protected void updateArtworkUi(PlayerStatus status) {
        Artwork artwork = status.getArtwork();

        ImageSwitcher switcher = mFullArtworkSwitcher;
        if (switcher != null) {
            if (artwork.isPresent()) {
                ListenableFuture<Bitmap> future = artwork.get(mFullSizeArtworkWidth);
                internalArtworkLoad(switcher, future);
            } else {
                internalArtworkSetNone(switcher);
            }
        }

        switcher = mThumbnailArtworkSwitcher;
        if (switcher != null) {
            if (artwork.isPresent()) {
                ListenableFuture<Bitmap> future = artwork.getThumbnail(mTinyArtworkWidth);
                internalArtworkLoad(switcher, future);
            } else {
                internalArtworkSetNone(switcher);
            }
        }
    }

    protected void postprocessVisibilities(PlayerStatus status, SparseIntArray visibilities) {
        // no implementation by default
    }

    protected boolean quickSetText(@Nullable TextView tv, CharSequence newText) {
        if (tv != null && !Objects.equal(tv.getText(), newText)) {
            tv.setText(newText);
            return true;
        }
        return false;
    }

    abstract protected void setProgress(PlayerStatus status, boolean estimate);

    protected boolean onPlayerControlLongClicked(int id) {
        if (id == R.id.pause_button) {
            PlayerCommands.sendStop();
            return true;
        }
        return false;
    }

    protected void onPlayerControlClicked(int id) {
        if (id == R.id.volume_button) {
            PlayerId playerId = mSbContext.getPlayerId();
            if (playerId != null) {
                if (isAdded()) {
                    VolumeFragment dlg = VolumeFragment.newInstance(playerId, false);
                    dlg.show(getParentFragmentManager(), VolumeFragment.TAG);
                }
            }
        } else if (id == R.id.next_button) {
            PlayerCommands.sendNextTrack();
        } else if (id == R.id.previous_button) {
            PlayerCommands.sendPreviousTrack();
        } else if (id == R.id.play_button) {
            PlayerCommands.sendPlay();
        } else if (id == R.id.pause_button) {
            PlayerCommands.sendPause();
        } else if (id == R.id.thumbsup_button || id == R.id.thumbsdown_button) {
            try {
                PlayerStatus status = mSbContext.getCheckedPlayerStatus();
                ButtonStatus buttonStatus;
                if (id == R.id.thumbsup_button) {
                    buttonStatus = status.getButtonStatus(PlayerButton.THUMBSUP).orNull();
                } else {
                    buttonStatus = status.getButtonStatus(PlayerButton.THUMBSDOWN).orNull();
                }
                if (buttonStatus != null && !buttonStatus.getCommands().isEmpty()) {
                    buttonStatus.markPressed(status);

                    FutureResult result = mSbContext.sendPlayerCommand(buttonStatus.getCommands());
                    Futures.addCallback(result, new ThumbResultReceiver(this), OSExecutors.getMainThreadExecutor());
                }
            } catch (PlayerNotFoundException e) {
                // ignore
            }
        } else {
            // nothing to do
        }
    }

    static class ThumbResultReceiver extends AbsFragmentResultReceiver<AbsNowPlayingFragment> {

        ThumbResultReceiver(AbsNowPlayingFragment fragment) {
            super(fragment);
        }

        @Override
        public void onEventualSuccess(AbsNowPlayingFragment fragment, SBResult result) {
            Iterator<?> it = result.getJsonResult().fieldNames();
            if (it.hasNext()) {
                Object o = it.next();

                fragment.showSnackbar(o.toString(), SnackbarLength.LONG);
            }
        }
    }

    private void internalSetSwitcherArtwork(ImageSwitcher switcher, Bitmap bmp,
                                            boolean showProgress) {
        // free memory
        ImageView current = (ImageView) switcher.getCurrentView();
        if (current != null) {
            current.setImageResource(0);
        }
        switcher.setImageDrawable(new BitmapDrawable(getResources(), bmp));
        if (mArtworkLoadingProgressBar != null) {
            mArtworkLoadingProgressBar.setVisibility(showProgress ? View.VISIBLE : View.INVISIBLE);
        }

        notifyNewArtwork(bmp, false);
    }

    private void notifyNewArtwork(@Nullable Bitmap bmp, boolean loading) {
        if (!(getActivity() instanceof ArtworkChangeCallback)) {
            return;
        }

        ArtworkChangeCallback cb = (ArtworkChangeCallback) getActivity();
        cb.notifyNewArtwork(bmp, loading);
    }

    private void internalSetSwitcherArtwork(ImageSwitcher switcher, @DrawableRes int rid, boolean showProgress) {
        // free memory
        ImageView current = (ImageView) switcher.getCurrentView();
        if (current != null) {
            current.setImageResource(0);
        }
        switcher.setImageResource(rid);
        if (mArtworkLoadingProgressBar != null) {
            mArtworkLoadingProgressBar.setVisibility(showProgress ? View.VISIBLE : View.INVISIBLE);
        }
        notifyNewArtwork(null, rid == R.drawable.artwork_loading);
    }

    private void internalArtworkSetNone(final ImageSwitcher switcher) {
        @SuppressWarnings("unchecked") ListenableFuture<Bitmap> oldFuture = (ListenableFuture<Bitmap>) switcher.getTag();
        if (oldFuture != null) {
            oldFuture.cancel(true);

            switcher.setTag(null);
        }

        internalSetSwitcherArtwork(switcher, R.drawable.artwork_missing, false);
    }

    private void internalArtworkLoad(final ImageSwitcher switcher,
                                     final ListenableFuture<Bitmap> future) {
        @SuppressWarnings("unchecked") ListenableFuture<Bitmap> oldFuture = (ListenableFuture<Bitmap>) switcher.getTag();
        if (Artwork.equivalent(oldFuture, future) && oldFuture != null && oldFuture.isDone()) {
            // same value, no need to update
            return;
        }

        if (oldFuture != null) {
            oldFuture.cancel(true);
        }

        switcher.setTag(future);

        Executor executor;
        if (!future.isDone()) {
            // artwork not ready, handle result on main thread
            executor = OSExecutors.getMainThreadExecutor();

            // show loading artwork
            internalSetSwitcherArtwork(switcher, R.drawable.artwork_loading, true);
        } else {
            // artwork is ready, just set it immediately
            executor = MoreExecutors.directExecutor();
        }

        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Bitmap bmp) {
                if (!isAdded()) {
                    return;
                }

                if (bmp != null) {
                    internalSetSwitcherArtwork(switcher, bmp, false);
                } else {
                    internalSetSwitcherArtwork(switcher, R.drawable.artwork_missing, false);
                }
            }

            @Override
            public void onFailure(@Nullable Throwable throwable) {
                if (future.isCancelled() || !isAdded()) {
                    return;
                }
                internalSetSwitcherArtwork(switcher, R.drawable.artwork_missing, false);
            }
        }, executor);
    }

    final private OnClickListener mControlClickListener = view -> onPlayerControlClicked(view.getId());

    final private View.OnLongClickListener mControlLongClickListener = view -> onPlayerControlLongClicked(view.getId());

    static class InterrimRunnable implements Runnable {
        final private WeakReference<AbsNowPlayingFragment> mFragmentRef;

        InterrimRunnable(AbsNowPlayingFragment fragment) {
            mFragmentRef = new WeakReference<>(fragment);
        }

        @Override
        public void run() {
            AbsNowPlayingFragment fragment = mFragmentRef.get();
            if (fragment == null) {
                return;
            }

            if (fragment.mInterimUpdateMode == InterimUpdateMode.CANCEL) {
                return;
            }

            if (fragment.mInterimUpdateMode == InterimUpdateMode.ON) {
                fragment.performInterimUpdate();
            }

            // try to update on an even number of seconds, this rounds
            // everything to the nearest 1000
            // 4100, retval=5000
            // 4990, retval=5000
            // 5000, retval=6000
            long nextTime = (SystemClock.uptimeMillis() / 1000);
            nextTime = (nextTime * 1000) + 1000;
            sHandler.postAtTime(this, nextTime);
        }

    }
}
