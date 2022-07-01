/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import com.google.common.collect.ImmutableMap;
import com.orangebikelabs.orangesqueeze.R;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Contains alternate mappings for images.
 *
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
public class ImageRemapper {

    final static private ImmutableMap<String, Integer> mInstance;

    static {
        // @formatter:off
        mInstance = new ImmutableMap.Builder<String, Integer>()

                .put("html/images/albums.png", R.drawable.ic_library_allsongs)
                .put("iPeng/plugins/CustomBrowse/html/images/custombrowse.png", R.drawable.ic_custombrowse)

                .build();
        // @formatter:on
    }

    public static ImmutableMap<String, Integer> getInstance() {
        return mInstance;
    }
}
