/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.BusProvider.ScopedBus;
import com.orangebikelabs.orangesqueeze.common.NavigationManager;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.co
 */
public class SBFragment extends Fragment {
    final protected ScopedBus mBus = BusProvider.newScopedInstance();

    @Nonnull
    protected SBContext mSbContext = SBContextProvider.uninitialized();


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSbContext = SBContextProvider.get();
        setHasOptionsMenu(true);
    }

    @Override
    public void onStop() {
        super.onStop();

        mBus.stop();
    }

    @Override
    public void onStart() {
        super.onStart();

        mBus.start();
    }

    /**
     * retrieve the activity-level instance of the manager
     */
    @Nonnull
    protected NavigationManager getNavigationManager() {
        DrawerActivity activity = (DrawerActivity) requireActivity();
        return activity.getNavigationManager();
    }
}
