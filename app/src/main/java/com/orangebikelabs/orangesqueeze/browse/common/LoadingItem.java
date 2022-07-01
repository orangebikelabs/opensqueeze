/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import javax.annotation.Nonnull;


public class LoadingItem extends Item {
    final private String mText;

    public LoadingItem(String text) {
        mText = text;
    }

    @Nonnull
    @Override
    public String getText() {
        return mText;
    }

    @Override
    @Nonnull
    public ItemType getBaseType() {
        return ItemType.IVT_LOADING;
    }
}