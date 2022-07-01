/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import android.content.Context;

import com.orangebikelabs.orangesqueeze.R;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class PlayNextAction extends PlayNowAction {

    public PlayNextAction(Context context) {
        this(context, R.string.playnext_desc, R.drawable.ic_skip_next);
    }

    protected PlayNextAction(Context context, int menuRid, int iconRid) {
        super(context, menuRid, iconRid);
    }

    @Override
    protected String getPlayCommand() {
        return "insert";
    }
}
