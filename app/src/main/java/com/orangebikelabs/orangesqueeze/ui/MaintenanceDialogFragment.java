/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.Nullable;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.SBDialogFragment;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.databinding.MaintenanceItemBinding;

import javax.annotation.Nonnull;

/**
 * Dialog that offers a few maintenance operations for the server and device.
 *
 * @author tsandee
 */
public class MaintenanceDialogFragment extends SBDialogFragment {
    enum MaintenanceOperations {
        // @formatter:off
        SERVER_RESCAN_PLAYLISTS(R.string.maintenance_server_rescan_playlists, R.string.maintenance_server_rescan_playlists_desc, false),
        SERVER_RESCAN_SERVER(R.string.maintenance_server_rescan, R.string.maintenance_server_rescan_desc, false),
        SERVER_WIPE_AND_RESCAN(R.string.maintenance_server_rescan_clear, R.string.maintenance_server_rescan_clear_desc, false),
        LOCAL_WIPE_CACHE(R.string.maintenance_local_cleardevicecache, R.string.maintenance_local_cleardevicecache_desc, true);
        // @formatter:on

        final private int mNameRid;
        final private int mDescriptionRid;
        final private boolean mIsSqueezeNetworkCapable;

        MaintenanceOperations(int nameRid, int descriptionRid, boolean squeezeNetworkCapable) {
            mNameRid = nameRid;
            mDescriptionRid = descriptionRid;
            mIsSqueezeNetworkCapable = squeezeNetworkCapable;
        }

        public int getNameRid() {
            return mNameRid;
        }

        public int getDescriptionRid() {
            return mDescriptionRid;
        }

        public boolean isSqueezeNetworkCapable() {
            return mIsSqueezeNetworkCapable;
        }
    }

    public static MaintenanceDialogFragment newInstance() {
        return new MaintenanceDialogFragment();
    }

    public MaintenanceDialogFragment() {
    }

    @Override
    @Nonnull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.maintenance_title)
                .setCancelable(true);
        boolean squeezeNetwork = mContext.getConnectionInfo().isSqueezeNetwork();
        ArrayAdapter<MaintenanceOperations> adapter = new ArrayAdapter<MaintenanceOperations>(requireContext(), R.layout.maintenance_item, R.id.text1) {
            @Override
            @Nonnull
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View retval = super.getView(position, convertView, parent);
                OSAssert.assertNotNull(retval, "view recycling failure");

                MaintenanceOperations op = getItem(position);
                OSAssert.assertNotNull(op, "item can't be null");

                MaintenanceItemBinding binding = MaintenanceItemBinding.bind(retval);

                binding.text1.setText(getContext().getString(op.getNameRid()));
                binding.text2.setText(getContext().getString(op.getDescriptionRid()));

                return retval;
            }
        };
        for (MaintenanceOperations op : MaintenanceOperations.values()) {
            if (!squeezeNetwork || op.isSqueezeNetworkCapable()) {
                adapter.add(op);
            }
        }
        builder.setAdapter(adapter, (dialog, which) -> {
            MaintenanceOperations op = MaintenanceOperations.values()[which];
            switch (op) {
                case LOCAL_WIPE_CACHE:
                    CacheServiceProvider.get().triggerWipe();
                    DatabaseAccess.getInstance(requireContext()).getGlobalQueries().vacuum();
                    break;
                case SERVER_RESCAN_PLAYLISTS:
                    mContext.newRequest("rescan", "playlists").submit(OSExecutors.getUnboundedPool());
                    break;
                case SERVER_RESCAN_SERVER:
                    mContext.newRequest("rescan").submit(OSExecutors.getUnboundedPool());
                    break;
                case SERVER_WIPE_AND_RESCAN:
                    mContext.newRequest("wipecache").submit(OSExecutors.getUnboundedPool());
                    break;
            }
            dialog.dismiss();
        });
        builder.setNegativeButton(R.string.close, (dialog, which) -> {
            // nothing to do
        });
        Dialog retval = builder.create();
        retval.setCanceledOnTouchOutside(true);
        return retval;
    }
}
