/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import android.content.Context;
import android.view.View;

import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.menu.MenuListAdapter;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class GlobalSearchListAdapter extends MenuListAdapter {
    public GlobalSearchListAdapter(Context context, ThumbnailProcessor processor) {
        super(context, processor);
    }

    @Override
    protected boolean bindActionButton(StandardMenuItem item, View actionButton) {
        if (item instanceof ExpandableSearchHeaderItem) {
            // enable action button for headers
            return true;
        } else {
            return super.bindActionButton(item, actionButton);
        }
    }
}
