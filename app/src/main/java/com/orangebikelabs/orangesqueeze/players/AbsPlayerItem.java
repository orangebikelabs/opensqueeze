/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.view.View;

import com.orangebikelabs.orangesqueeze.common.SBContext;

/**
 * @author tbsandee@orangebikelabs.com
 */
abstract public class AbsPlayerItem {
    private View mView;

    abstract protected int getViewType();

    abstract protected int getLayoutRid();

    protected void initView(View view) {
        mView = view;
    }

    public View getView() {
        return mView;
    }

    public void onClick(SBContext context, ManagePlayersFragment fragment) {

    }

    abstract protected void updateView(View view);

    abstract protected String getName();
}
