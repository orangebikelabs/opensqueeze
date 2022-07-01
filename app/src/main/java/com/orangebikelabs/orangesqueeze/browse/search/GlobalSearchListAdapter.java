/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
