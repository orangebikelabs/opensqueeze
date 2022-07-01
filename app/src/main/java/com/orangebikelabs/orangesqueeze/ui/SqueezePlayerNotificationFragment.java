/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class SqueezePlayerNotificationFragment extends DialogFragment {
    private static final String TAG = "SqueezePlayer";

    /**
     * conditionally displayes the volume control notification alert. returns true if we display the alert, false if normal processing
     * should occur.
     */
    public static boolean showIfNecessary(FragmentManager fragmentManager) {
        boolean showed = false;
        if (!SBPreferences.get().isShowedSqueezePlayerStartupOption()) {
            SqueezePlayerNotificationFragment frag = new SqueezePlayerNotificationFragment();
            frag.show(fragmentManager, TAG);
            showed = true;
        }
        return showed;
    }

    @Override
    @Nonnull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());

        builder.setTitle(R.string.pref_autolaunch_squeezeplayer_title);
        builder.setMessage(R.string.pref_autolaunch_squeezeplayer_message);
        builder.setNegativeButton(R.string.no, mClickListener);
        builder.setPositiveButton(R.string.yes, mClickListener);

        Dialog retval = builder.create();
        return retval;
    }

    final private DialogInterface.OnClickListener mClickListener = (dialog, which) -> {
        if (!isAdded()) {
            return;
        }
        try {
            SBPreferences prefs = SBPreferences.get();
            prefs.setShowedSqueezePlayerStartupOption(true);
            if (which == DialogInterface.BUTTON_POSITIVE) {
                prefs.setShouldAutoLaunchSqueezePlayer(true);
            }
            dismissAllowingStateLoss();
        } catch (IllegalStateException e) {
            // ignore
        }
    };
}
