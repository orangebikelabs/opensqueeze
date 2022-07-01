/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import android.content.Context;

import com.orangebikelabs.orangesqueeze.R;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class AddToPlaylistAction extends PlayNextAction {
    public AddToPlaylistAction(Context context) {
        super(context, R.string.playadd_desc, R.drawable.ic_add);
    }

    @Override
    protected String getPlayCommand() {
        return "add";
    }
}
