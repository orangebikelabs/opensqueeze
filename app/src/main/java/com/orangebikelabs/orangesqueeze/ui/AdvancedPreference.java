/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.ui;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import androidx.preference.Preference;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class AdvancedPreference extends Preference {
    public AdvancedPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        getContext().startActivity(new Intent(getContext(), AdvancedPreferenceActivity.class));
    }
}
