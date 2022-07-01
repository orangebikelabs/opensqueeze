/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse;

import android.content.Context;

import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;

import javax.annotation.Nonnull;

public class ActionItem extends Item {

    final protected int mActionId;
    final protected int mSortOrder;
    final protected String mText;

    public ActionItem(Context context, int actionId, int sortOrder) {
        mActionId = actionId;
        mSortOrder = sortOrder;
        mText = context.getString(actionId);
    }

    @Override
    @Nonnull
    public ItemType getBaseType() {
        return ItemType.IVT_ACTION;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    @Nonnull
    public String getText() {
        return mText;
    }
}