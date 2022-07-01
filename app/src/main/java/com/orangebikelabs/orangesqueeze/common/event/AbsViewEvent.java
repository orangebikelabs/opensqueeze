/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;
import androidx.fragment.app.Fragment;
import android.view.View;
import android.view.ViewParent;

/**
 * @author tsandee
 */
@Keep
abstract class AbsViewEvent {
    final private View mView;

    protected AbsViewEvent(View view) {
        mView = view;
    }

    public boolean appliesTo(Fragment fragment) {
        boolean found = false;
        View fragmentView = fragment.getView();
        ViewParent p = mView.getParent();
        while (p != null) {
            if (p == fragmentView) {
                found = true;
                break;
            }
            p = p.getParent();
        }
        return found;
    }
}
