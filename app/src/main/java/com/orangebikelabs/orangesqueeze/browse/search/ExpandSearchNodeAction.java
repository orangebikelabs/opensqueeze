/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import android.content.Context;
import androidx.fragment.app.Fragment;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.actions.AbsAction;

public class ExpandSearchNodeAction extends AbsAction<ExpandableSearchHeaderItem> {

    private String mAutoExpandKey;

    public ExpandSearchNodeAction(Context context) {
        super(context.getString(R.string.globalsearch_expand_nodeinline_on), 0);
    }

    @Override
    public boolean initialize(ExpandableSearchHeaderItem item) {
        mAutoExpandKey = GlobalSearchPreferences.getAutoExpandKey(item.getMenuElement(), item.getGoAction());

        return mAutoExpandKey != null && !item.isExpanded();
    }

    @Override
    public boolean execute(Fragment controller) {
        GlobalSearchPreferences.setAutoExpandItem(mAutoExpandKey, true);
        ((GlobalSearchResultsFragment) controller).requery(null);
        return true;
    }
}