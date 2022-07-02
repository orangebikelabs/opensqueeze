/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
