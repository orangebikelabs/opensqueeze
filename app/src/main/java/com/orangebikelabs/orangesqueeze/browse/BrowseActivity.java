/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.appcompat.app.ActionBar;
import android.view.MenuItem;
import android.view.View;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.DrawerActivity;
import com.orangebikelabs.orangesqueeze.artwork.ListPreloadFragment;
import com.orangebikelabs.orangesqueeze.artwork.ShowArtworkFragment;
import com.orangebikelabs.orangesqueeze.browse.node.BrowseNodeFragment;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.event.AppPreferenceChangeEvent;
import com.orangebikelabs.orangesqueeze.ui.MainActivity;
import com.orangebikelabs.orangesqueeze.ui.TinyNowPlayingFragment;
import com.squareup.otto.Subscribe;

import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class BrowseActivity extends DrawerActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBus.register(mEventReceiver);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onLoadNavigationItem(NavigationItem navigationItem) {
        super.onLoadNavigationItem(navigationItem);

        Fragment newFragment;
        switch (navigationItem.getType()) {
            case BROWSENODE:
                newFragment = BrowseNodeFragment.newInstance(navigationItem);
                break;
            case BROWSEREQUEST:
                newFragment = BrowseRequestFragment.newInstance(navigationItem);
                break;
            case BROWSEARTWORK:
                newFragment = ShowArtworkFragment.newInstance(navigationItem);
                break;
            default:
                throw new IllegalStateException("Unexpected NavigationItem:" + navigationItem);
        }

        Bundle extras = new Bundle(getIntent().getExtras());
        NavigationItem.Companion.removeNavigationItem(extras);

        Bundle arguments = newFragment.requireArguments();
        arguments.putAll(extras);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.add(R.id.content_frame, newFragment);
        transaction.add(ListPreloadFragment.newInstance(), "preload");
        transaction.add(R.id.tinynowplaying_frame, TinyNowPlayingFragment.newInstance());
        transaction.commitNow();
    }

    @Override
    protected void onStart() {
        super.onStart();

        syncHeaderFooterState();
    }

    @Override
    protected void onDestroy() {

        mBus.unregister(mEventReceiver);

        super.onDestroy();
    }

    @Override
    protected boolean allowSnackbarDisplay() {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void applyDrawerState(DrawerState drawerState) {
        if (drawerState == DrawerState.BROWSE) {
            getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle(R.string.left_drawer_title);
        } else if (drawerState == DrawerState.PLAYER) {
            getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        } else {
            getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent upIntent = MainActivity.Companion.newIntent(this);
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is not part of the application's task, so create a new task
                // with a synthesized back stack.
                TaskStackBuilder.create(this).addNextIntent(upIntent).startActivities();
                finish();
            } else {
                // This activity is part of the application's task, so simply
                // navigate up to the hierarchical parent activity.
                finish();
            }
            // TODO do we want transitions for exiting?
            //overridePendingTransition(R.anim.in_from_left, android.R.anim.fade_out);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    protected void syncHeaderFooterState() {
        if (!SBPreferences.get().isBrowseActionBarEnabled()) {
            getSupportActionBar().hide();
        } else {
            getSupportActionBar().show();
        }

        View tinyContainer = findViewById(R.id.tinynowplaying_frame);
        if (tinyContainer != null) {
            boolean visible = SBPreferences.get().isBrowseNowPlayingBarEnabled();
            tinyContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenAppPreferenceChanges(AppPreferenceChangeEvent event) {
            // just reset on any pref change for simplicity
            syncHeaderFooterState();
        }
    };
}
