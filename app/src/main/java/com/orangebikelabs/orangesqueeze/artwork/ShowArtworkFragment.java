/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.SBFragment;
import com.orangebikelabs.orangesqueeze.cache.CacheFuture;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.common.NavigationCommandSet;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.Drawables;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fragment that shows full-size artwork.
 */
public class ShowArtworkFragment extends SBFragment {
    @Nonnull
    public static ShowArtworkFragment newInstance(NavigationItem item) {
        ShowArtworkFragment retval = new ShowArtworkFragment();
        Bundle args = new Bundle();
        NavigationItem.Companion.putNavigationItem(args, item);
        retval.setArguments(args);
        return retval;
    }

    @Nullable
    private CacheFuture<ArtworkCacheData> mArtworkFuture;

    @Nullable
    private String mArtworkId;

    private ImageView mArtworkView;
    private ProgressBar mProgress;
    private boolean mStarted;

    public ShowArtworkFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NavigationItem item = NavigationItem.Companion.getNavigationItem(getArguments());
        OSAssert.assertNotNull(item, "must be nonnull");

        String url = item.getArtworkUrl();
        if (url == null) {
            NavigationCommandSet ncs = item.getRequestCommandSet();
            if (ncs != null) {
                List<String> commands = ncs.getCommands();
                if (commands.size() >= 2 && commands.get(0).equals("artwork")) {
                    mArtworkId = commands.get(1);
                }
            }
        } else {
            mArtworkId = url;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.showbigartwork, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mArtworkView = view.findViewById(R.id.artwork);
        mProgress = view.findViewById(R.id.progress);
    }

    @Override
    public void onStart() {
        super.onStart();

        mStarted = true;

        if (mArtworkId == null) {
            mArtworkView.setImageDrawable(Drawables.getNoArtworkDrawableTinted(requireContext()));
            mArtworkView.setContentDescription(getString(R.string.artwork_missing_desc));
            mProgress.setVisibility(View.GONE);
            return;
        }

        CacheServiceProvider.get().aboutToDecodeLargeArtwork();
        ArtworkType type;
        if (mArtworkId.matches("^[\\d[a-f][A-F]]+$")) {
            type = ArtworkType.ALBUM_FULL;
        } else {
            type = ArtworkType.SERVER_RESOURCE_FULL;
        }
        ArtworkCacheRequestCallback request = Artwork.newCacheRequest(requireContext(), mArtworkId, type, Artwork.getFullSizeArtworkWidth(requireContext()));
        mArtworkFuture = CacheServiceProvider.get().load(request, OSExecutors.getUnboundedPool());
        mProgress.setVisibility(View.VISIBLE);
        mArtworkView.setContentDescription(getString(R.string.artwork_loading_desc));
        mArtworkView.setImageResource(R.drawable.artwork_loading);

        final Future<?> finalFuture = mArtworkFuture;
        Futures.addCallback(mArtworkFuture, new FutureCallback<ArtworkCacheData>() {

            @Override
            public void onSuccess(@Nullable ArtworkCacheData artworkCacheData) {
                OSAssert.assertNotNull(artworkCacheData, "can't be null");

                try {
                    RecyclableBitmap bmp = artworkCacheData.decodeBitmap();
                    if (bmp != null) {
                        OSExecutors.getMainThreadExecutor().execute(() -> {
                            if (mStarted) {
                                mProgress.setVisibility(View.INVISIBLE);
                                mArtworkView.setImageBitmap(bmp.get());
                                mArtworkView.setContentDescription(getString(R.string.player_artwork_desc));
                            }
                        });
                    }
                } catch (IOException e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                // do nothing if the load was cancelled
                if (finalFuture.isCancelled()) {
                    return;
                }

                OSLog.w(t.getMessage(), t);

                OSExecutors.getMainThreadExecutor().execute(() -> {
                    if (mStarted) {
                        mProgress.setVisibility(View.INVISIBLE);
                        mArtworkView.setContentDescription(getString(R.string.artwork_missing_desc));
                        mArtworkView.setImageDrawable(Drawables.getNoArtworkDrawableTinted(requireContext()));
                    }
                });
            }
        }, OSExecutors.getUnboundedPool());
    }

    @Override
    public void onStop() {
        super.onStop();

        if (mArtworkFuture != null) {
            mArtworkFuture.cancel(true);
            mArtworkFuture = null;
        }

        mArtworkView.setImageResource(0);

        mStarted = false;
    }
}
