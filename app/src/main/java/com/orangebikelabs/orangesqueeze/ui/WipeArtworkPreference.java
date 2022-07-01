/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
