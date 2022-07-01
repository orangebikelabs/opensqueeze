/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import javax.annotation.Nonnull;


public class TextItem extends Item {
    @Nonnull
    final protected String mText;

    public TextItem(String text) {
        mText = text;
    }

    @Override
    @Nonnull
    public ItemType getBaseType() {
        return ItemType.IVT_TEXT;
    }

    @Override
    public boolean isSingleItemConsideredEmpty() {
        return true;
    }

    @Override
    @Nonnull
    public String getText() {
        return mText;
    }
}