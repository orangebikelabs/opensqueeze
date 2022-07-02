/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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