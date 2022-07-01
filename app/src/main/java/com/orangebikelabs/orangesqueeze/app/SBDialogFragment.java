/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.BusProvider.ScopedBus;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class SBDialogFragment extends DialogFragment {
    final protected ScopedBus mBus = BusProvider.newScopedInstance();

    @Nonnull
    protected SBContext mContext = SBContextProvider.uninitialized();

    protected ServerConnectionServiceHelper mServerConnectionServiceHelper = new ServerConnectionServiceHelper(true);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(STYLE_NORMAL, 0);
        mContext = SBContextProvider.get();
    }

    @Override
    public void onStop() {
        super.onStop();

        mServerConnectionServiceHelper.onStop();

        mBus.stop();
    }

    @Override
    public void onStart() {
        try {
            super.onStart();

            mServerConnectionServiceHelper.onStart();

            mBus.start();
        } catch(RuntimeException e) {
            // ignore occasional error on Lenovo Yoga devices
        }
    }
}
