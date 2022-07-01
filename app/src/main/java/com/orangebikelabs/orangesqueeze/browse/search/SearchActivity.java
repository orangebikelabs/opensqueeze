/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.appcompat.app.ActionBar;
import android.view.MenuItem;

import com.google.common.base.Strings;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.DrawerActivity;
import com.orangebikelabs.orangesqueeze.common.Actions;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.ui.MainActivity;
import com.orangebikelabs.orangesqueeze.ui.TinyNowPlayingFragment;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class SearchActivity extends DrawerActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar ab = getSupportActionBar();
        ab.setHomeButtonEnabled(true);
        ab.setDisplayHomeAsUpEnabled(true);
        ab.setTitle(getCurrentItem().getName());

        if (savedInstanceState == null) {
            String query = Strings.nullToEmpty(getQuery(getIntent()));

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.content_frame, GlobalSearchResultsFragment.newInstance(query));
            transaction.add(R.id.tinynowplaying_frame, TinyNowPlayingFragment.newInstance());

            transaction.commitNow();
        }
    }

    @Override
    protected void onNewIntentBehavior(@Nonnull Intent intent) {
        String query = getQuery(intent);
        if (query == null) {
            // redirect to main activity with search active
            Intent redirect = MainActivity.Companion.newIntent(this);
            redirect.setAction(Actions.ACTION_GO_SEARCH);
            startActivity(redirect);

            finish();
        } else {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.content_frame, GlobalSearchResultsFragment.newInstance(query));
            transaction.commitNow();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent upIntent = new Intent(this, MainActivity.class);
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
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    private String getQuery(Intent intent) {
        String query = intent.getStringExtra(SearchManager.QUERY);
        if (query != null) {
            return query;
        }
        NavigationItem item = NavigationItem.Companion.getNavigationItem(intent);
        if (item != null) {
            return item.getSearchQuery();
        }
        return null;
    }
}
