/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.node;

import android.content.Context;

import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.menu.MenuListAdapter;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class NodeItemAdapter extends MenuListAdapter {
    public NodeItemAdapter(Context context, ThumbnailProcessor processor) {
        super(context, processor);
    }
}
