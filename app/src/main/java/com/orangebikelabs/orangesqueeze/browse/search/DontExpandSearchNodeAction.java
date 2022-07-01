/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import android.content.Context;
import androidx.fragment.app.Fragment;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.actions.AbsAction;

public class DontExpandSearchNodeAction extends AbsAction<ExpandableSearchHeaderItem> {

    private String mAutoExpandKey;

    public DontExpandSearchNodeAction(Context context) {
        super(context.getString(R.string.globalsearch_expand_nodeinline_off), 0);
    }

    @Override
    public boolean initialize(ExpandableSearchHeaderItem item) {
        mAutoExpandKey = GlobalSearchPreferences.getAutoExpandKey(item.getMenuElement(), item.getGoAction());

        return mAutoExpandKey != null && item.isExpanded();
    }

    @Override
    public boolean execute(Fragment controller) {
        GlobalSearchPreferences.setAutoExpandItem(mAutoExpandKey, false);
        ((GlobalSearchResultsFragment) controller).requery(null);
        return true;
    }
}