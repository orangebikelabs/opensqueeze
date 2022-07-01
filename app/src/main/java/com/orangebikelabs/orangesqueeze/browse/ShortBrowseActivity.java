/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.SBActivity;
import com.orangebikelabs.orangesqueeze.artwork.ShowArtworkFragment;
import com.orangebikelabs.orangesqueeze.browse.node.BrowseNodeFragment;
import com.orangebikelabs.orangesqueeze.common.NavigationCommandSet;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.ui.MainActivity;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class ShortBrowseActivity extends SBActivity {

    /**
     * create a bare browse intent, with no navigation stack
     */
    @Nonnull
    public static Intent createBrowseIntent(Context context, String title, List<String> commands, List<String> params, @Nullable Bundle args) {
        Intent retval = new Intent(context, ShortBrowseActivity.class);

        NavigationCommandSet ncs = new NavigationCommandSet(commands, params);
        NavigationItem navigationItem = NavigationItem.Companion.newBrowseRequestItem(title, ncs, null, null, null, null, false, null);

        NavigationItem.Companion.putNavigationItem(retval, navigationItem);
        retval.putExtra(AbsBrowseFragment.PARAM_SHORT_MODE, true);
        if (args != null) {
            retval.putExtras(args);
        }
        return retval;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.toolbar_activity);
        setSupportActionBar(findViewById(R.id.toolbar));

        Intent intent = getIntent();

        Bundle extras = intent.getExtras();
        if (extras == null) {
            throw new IllegalStateException("invalid intent:" + intent);
        }
        NavigationItem navigationItem = NavigationItem.Companion.getNavigationItem(intent);
        if (navigationItem == null) {
            throw new IllegalStateException("invalid intent:" + intent);
        }

        setTitle(navigationItem.getName());

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setDisplayShowHomeEnabled(true);
            ab.setDisplayShowTitleEnabled(true);
        }
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
                throw new IllegalStateException("invalid intent:" + intent);
        }

        NavigationItem.Companion.removeNavigationItem(extras);
        Bundle arguments = newFragment.requireArguments();
        arguments.putAll(extras);

        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.toolbar_content, newFragment);
            transaction.commitNow();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
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
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }
}
