/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
