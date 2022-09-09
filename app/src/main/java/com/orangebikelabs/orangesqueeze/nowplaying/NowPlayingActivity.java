/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;

import android.view.Menu;
import android.view.MenuItem;

import com.orangebikelabs.orangesqueeze.BuildConfig;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.DrawerActivity;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.common.Drawables;
import com.orangebikelabs.orangesqueeze.common.MenuTools;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState;
import com.orangebikelabs.orangesqueeze.ui.MainActivity;
import com.squareup.otto.Subscribe;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class NowPlayingActivity extends DrawerActivity implements CurrentPlaylistItemCallback, ArtworkChangeCallback {

    final public static String EXTRA_RECREATE_MAIN_ACTIVITY_ON_UP = BuildConfig.APPLICATION_ID + ".extra.recreateMainActivityOnUp";
    final private static String STATE_SHOWING_PLAYLIST = BuildConfig.APPLICATION_ID + ".state.showingPlaylist";

    @Nullable
    private PlaylistItem mCurrentPlaylistItem;

    private boolean mRecreateMainActivityOnUp;
    private boolean mShowingPlaylist = false;
    private MenuItem mHidePlaylistItem;
    private BitmapDrawable mHidePlaylistItemDrawable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialized in onCreate() above
        getDrawerBinding().drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GRAVITY_PLAYER_DRAWER);
        mRecreateMainActivityOnUp = getIntent().getBooleanExtra(EXTRA_RECREATE_MAIN_ACTIVITY_ON_UP, false);

        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.content_frame, new NowPlayingFragment(), NowPlayingFragment.TAG);

            CurrentPlaylistFragment currentPlaylistFragment = CurrentPlaylistFragment.newInstance();
            transaction.add(R.id.content_frame, currentPlaylistFragment, CurrentPlaylistFragment.TAG);
            transaction.hide(currentPlaylistFragment);
            transaction.commitNow();
        }

        mBus.register(mEventReceiver);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setDisplayShowTitleEnabled(true);

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(android.R.anim.fade_in, R.anim.out_to_bottom);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mBus.unregister(mEventReceiver);
    }

    @Override
    protected void onStart() {
        super.onStart();

        CacheServiceProvider.get().setUsingLargeArtwork(true);

        setTitle(mContext.getPlayerStatus());
    }

    @Override
    protected void onStop() {
        super.onStop();

        CacheServiceProvider.get().setUsingLargeArtwork(false);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuTools.setVisible(menu, R.id.menu_nowplaying_showplaylist, !mShowingPlaylist);
        MenuTools.setVisible(menu, R.id.menu_nowplaying_hideplaylist, mShowingPlaylist);

        if (getResources().getBoolean(R.bool.compact_nowplaying_actionbar)) {
            MenuItem playersItem = menu.findItem(R.id.menu_players);
            if (playersItem != null) {
                playersItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }

            MenuItem searchItem = menu.findItem(R.id.menu_search);
            if (searchItem != null) {
                searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.nowplaying, menu);

        mHidePlaylistItem = menu.findItem(R.id.menu_nowplaying_hideplaylist);
        setHidePlaylistItemDrawable();

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent upIntent = new Intent(this, MainActivity.class);
            if (NavUtils.shouldUpRecreateTask(this, upIntent) || mRecreateMainActivityOnUp) {
                // This activity is not part of the application's task, so create a new task
                // with a synthesized back stack.
                TaskStackBuilder.create(this).addNextIntent(upIntent).startActivities();
                finish();
            } else {
                // This activity is part of the application's task, so simply
                // navigate up to the hierarchical parent activity.
                finish();
            }
            overridePendingTransition(android.R.anim.fade_in, R.anim.out_to_bottom);
            return true;
        } else if (item.getItemId() == R.id.menu_nowplaying_showplaylist) {
            CurrentPlaylistFragment playlistFragment = (CurrentPlaylistFragment) getSupportFragmentManager().findFragmentByTag(CurrentPlaylistFragment.TAG);
            OSAssert.assertNotNull(playlistFragment, "expected fragment to be nonnull");

            NowPlayingFragment nowPlayingFragment = (NowPlayingFragment) getSupportFragmentManager().findFragmentByTag(NowPlayingFragment.TAG);
            OSAssert.assertNotNull(nowPlayingFragment, "expected fragment to be nonnull");

            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.in_from_bottom, android.R.anim.fade_out)
                    .hide(nowPlayingFragment)
                    .show(playlistFragment)
                    .commitNow();
            mShowingPlaylist = true;
            invalidateOptionsMenu();
            return true;
        } else if (item.getItemId() == R.id.menu_nowplaying_hideplaylist) {
            CurrentPlaylistFragment playlistFragment = (CurrentPlaylistFragment) getSupportFragmentManager().findFragmentByTag(CurrentPlaylistFragment.TAG);
            OSAssert.assertNotNull(playlistFragment, "expected fragment to be nonnull");

            NowPlayingFragment nowPlayingFragment = (NowPlayingFragment) getSupportFragmentManager().findFragmentByTag(NowPlayingFragment.TAG);
            OSAssert.assertNotNull(nowPlayingFragment, "expected fragment to be nonnull");

            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, R.anim.out_to_bottom)
                    .hide(playlistFragment)
                    .show(nowPlayingFragment)
                    .commitNow();
            mShowingPlaylist = false;
            invalidateOptionsMenu();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void setHidePlaylistItemDrawable() {
        if (mHidePlaylistItem == null) {
            return;
        }

        if (mHidePlaylistItemDrawable != null) {
            mHidePlaylistItem.setIcon(mHidePlaylistItemDrawable);
        } else {
            Drawable d = ContextCompat.getDrawable(this, R.drawable.ic_playlist);
            OSAssert.assertNotNull(d, "drawable should be non-null");

            Drawable newDrawable = Drawables.getTintedDrawable(this, d);
            mHidePlaylistItem.setIcon(newDrawable);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // save hidden/shown state for now playing and playlist fragments
        outState.putBoolean(STATE_SHOWING_PLAYLIST, mShowingPlaylist);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mShowingPlaylist = savedInstanceState.getBoolean(STATE_SHOWING_PLAYLIST, false);

        // hide one of these fragments
        String hideTag;
        if (mShowingPlaylist) {
            hideTag = NowPlayingFragment.TAG;
        } else {
            hideTag = CurrentPlaylistFragment.TAG;
        }
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(hideTag);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction().hide(fragment).commitNow();
        }
    }

    @Override
    public void notifyNewArtwork(@Nullable Bitmap bmp, boolean loading) {
        if (bmp == null) {
            // don't clear the drawable if we're just loading
            if (!loading) {
                mHidePlaylistItemDrawable = null;
            }
        } else {
            int dimension = (int) (getResources().getDisplayMetrics().density * 30);
            Bitmap created = Bitmap.createScaledBitmap(bmp, dimension, dimension, false);
            mHidePlaylistItemDrawable = new BitmapDrawable(getResources(), created);
        }

        setHidePlaylistItemDrawable();
    }


    /**
     * Set the title based on the supplied player status object
     *
     * @param playerStatus optional player status for title
     */
    public void setTitle(@Nullable PlayerStatus playerStatus) {

        String title;

        if (playerStatus != null) {
            String playerName = playerStatus.getName();
            if (playerStatus.isPowered()) {
                if (playerStatus.isConnected()) {
                    switch (playerStatus.getMode()) {
                        case STOPPED:
                            title = getString(R.string.nowplaying_activity_title_stopped_html, playerName);
                            break;
                        case PLAYING:
                            title = getString(R.string.nowplaying_activity_title_playing_html, playerName, playerStatus.getPlaylistIndex() + 1, playerStatus.getPlaylistTrackCount());
                            break;
                        case PAUSED:
                            title = getString(R.string.nowplaying_activity_title_paused_html, playerName);
                            break;
                        default:
                            throw new IllegalStateException("unexpected player mode: " + playerStatus.getMode());
                    }
                } else {
                    title = getString(R.string.nowplaying_activity_title_disconnected_html, playerName);
                }
            } else {
                title = getString(R.string.nowplaying_activity_title_off_html, playerName);
            }
        } else {
            title = getString(R.string.nowplaying_activity_title_noplayers_html);
        }
        setTitle(HtmlCompat.fromHtml(title, 0));
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenCurrentPlayerStatusChanges(CurrentPlayerState event) {
            setTitle(event.getPlayerStatus());
        }
    };

    @Nullable
    @Override
    public PlaylistItem getCurrentPlaylistItem() {
        return mCurrentPlaylistItem;
    }

    @Override
    public void setCurrentPlaylistItem(@Nullable PlaylistItem item) {
        mCurrentPlaylistItem = item;
    }
}
