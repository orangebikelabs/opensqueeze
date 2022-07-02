/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
