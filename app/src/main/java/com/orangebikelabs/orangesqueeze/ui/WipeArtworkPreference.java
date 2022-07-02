/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui;

import android.content.Context;
import android.util.AttributeSet;

import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;

import androidx.preference.DialogPreference;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class WipeArtworkPreference extends DialogPreference {
    public WipeArtworkPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        CacheServiceProvider.get().triggerWipe();
    }
}
