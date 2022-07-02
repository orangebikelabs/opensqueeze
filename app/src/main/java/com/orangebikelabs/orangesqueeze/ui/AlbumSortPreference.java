/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.browse.ShortBrowseActivity;

import java.util.Collections;

import androidx.preference.Preference;

/**
 * @author tsandee
 */
public class AlbumSortPreference extends Preference {

    public AlbumSortPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        super.onClick();

        Intent intent = ShortBrowseActivity.createBrowseIntent(getContext(), getContext().getString(R.string.pref_browse_albumsort_title),
                Collections.singletonList("jivealbumsortsettings"), Collections.singletonList("menu:radio"), null);
        getContext().startActivity(intent);
    }
}
