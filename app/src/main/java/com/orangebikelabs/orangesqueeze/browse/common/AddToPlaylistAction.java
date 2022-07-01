/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
