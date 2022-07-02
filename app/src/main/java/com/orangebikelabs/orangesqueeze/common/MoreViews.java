/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.IdRes;
import android.view.View;

import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class MoreViews {
    /**
     * sets new tag value, returns old
     */
    @Nullable
    static public Object getAndSetTag(View view, @IdRes int id, @Nullable Object value) {
        OSAssert.assertMainThread();

        Object previous = view.getTag(id);
        if (previous != value) {
            view.setTag(id, value);
        }
        return previous;
    }
}
