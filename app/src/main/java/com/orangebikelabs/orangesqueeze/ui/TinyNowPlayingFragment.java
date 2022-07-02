/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui;

import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ProgressBar;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.AutoSizeTextHelper;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.NavigationManager;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState;
import com.orangebikelabs.orangesqueeze.nowplaying.AbsNowPlayingFragment;
import com.squareup.otto.Subscribe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class TinyNowPlayingFragment extends AbsNowPlayingFragment {
    @Nonnull
    public static TinyNowPlayingFragment newInstance() {
        return new TinyNowPlayingFragment();
    }

    final protected AutoSizeTextHelper mAutoSizeHelper = new AutoSizeTextHelper();

    protected ProgressBar mProgressView;
    protected View mProgressGradientView;
    protected Animation mProgressAnimation;
    protected ViewGroup mTinyContainer;
    protected boolean mCompactMode;
    protected boolean mAnimationsInit;

    public TinyNowPlayingFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCompactMode = getResources().getBoolean(R.bool.tiny_nowplaying_compact_mode);
        mBus.register(mEventReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBus.unregister(mEventReceiver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tiny_nowplaying, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTinyContainer = view.findViewById(R.id.tiny_nowplaying_container);
        mTinyContainer.setOnClickListener(v -> {
            NavigationManager manager = getNavigationManager();
            manager.startActivity(manager.newNowPlayingIntent());

            requireActivity().overridePendingTransition(R.anim.in_from_bottom, android.R.anim.fade_out);
        });
        mProgressView = view.findViewById(R.id.tiny_track_progress);
        mProgressGradientView = view.findViewById(R.id.nowplaying_progress_gradient);

        mAutoSizeHelper.applyAutoSize(mArtistText, 1);
        mAutoSizeHelper.applyAutoSize(mTrackText, 1);

        disableArtworkAnimations();
    }

    @Nullable
    @Override
    protected View getSnackbarView() {
        return mTinyContainer;
    }

    protected void disableArtworkAnimations() {
        OSAssert.assertNotNull(mThumbnailArtworkSwitcher, "can't be null");

        mThumbnailArtworkSwitcher.setInAnimation(null);
        mThumbnailArtworkSwitcher.setOutAnimation(null);

        mProgressGradientView.clearAnimation();

        if (mProgressAnimation != null) {
            mProgressAnimation.reset();
            mProgressAnimation = null;
        }

        mAnimationsInit = false;
    }

    protected void enableArtworkAnimations() {
        if (!mAnimationsInit) {
            // once player update has occurred, init the artwork animations
            OSAssert.assertNotNull(mThumbnailArtworkSwitcher, "can't be null");

            mThumbnailArtworkSwitcher.setInAnimation(getActivity(), R.anim.in_from_right);
            mThumbnailArtworkSwitcher.setOutAnimation(getActivity(), R.anim.out_to_left);
            mAnimationsInit = true;
        }
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenCurrentPlayerStatusChanges(CurrentPlayerState event) {
            PlayerStatus ps = event.getPlayerStatus();
            if (ps == null) {
                return;
            }

            enableArtworkAnimations();

            switch (ps.getMode()) {
                case PAUSED:
                case STOPPED:
                    if (mProgressAnimation != null) {
                        mProgressGradientView.clearAnimation();

                        mProgressAnimation.reset();
                        mProgressAnimation = null;
                    }
                    break;
                case PLAYING:
                    if (mProgressAnimation == null) {
                        // because view is offset at -width (off-screen), the
                        // distance to animate is progressview width + the gradient
                        // width

                        int animateDistance = mProgressGradientView.getWidth() + mProgressView.getWidth();

                        mProgressAnimation = new TranslateAnimation(Animation.ABSOLUTE, 0, Animation.ABSOLUTE, animateDistance, Animation.ABSOLUTE, 0,
                                Animation.ABSOLUTE, 0);
                        mProgressAnimation.setRepeatCount(Animation.INFINITE);
                        mProgressAnimation.setRepeatMode(Animation.RESTART);
                        mProgressAnimation.setDuration(getResources().getInteger(R.integer.nowplaying_progress_animation_duration));
                        mProgressGradientView.startAnimation(mProgressAnimation);

                    }
                    break;
            }
        }
    };

    @Override
    public void onStop() {
        super.onStop();

        disableArtworkAnimations();
    }

    @Override
    protected void updatePlayerUI(PlayerStatus status) {
        super.updatePlayerUI(status);

        quickSetText(mTrackText, getTrackText(status));
        setProgress(status, false);
    }

    @Override
    protected void postprocessVisibilities(PlayerStatus status, SparseIntArray visibilities) {
        if (mCompactMode) {
            if (status.isThumbsUpEnabled()) {
                visibilities.put(R.id.play_button, View.GONE);
                visibilities.put(R.id.pause_button, View.GONE);
                visibilities.put(R.id.next_button, View.GONE);
            }
            visibilities.put(R.id.previous_button, View.GONE);
            visibilities.put(R.id.volume_button, View.GONE);
        }
        visibilities.put(R.id.shuffle_button, View.GONE);
        visibilities.put(R.id.repeat_button, View.GONE);
    }

    @Override
    protected void setProgress(PlayerStatus status, boolean estimate) {
        int elapsed = (int) (status.getElapsedTime(estimate));
        int total = (int) (status.getTotalTime());
        if (total != 0) {
            mProgressView.setProgress(elapsed);
        } else {
            mProgressView.setProgress(0);
        }
        mProgressView.setMax(total);
    }
}
