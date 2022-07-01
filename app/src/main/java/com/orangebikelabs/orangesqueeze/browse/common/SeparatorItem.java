/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import javax.annotation.Nonnull;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class SeparatorItem extends Item {

    @Nonnull
    final private String mSectionName;

    public SeparatorItem() {
        this("");
    }

    public SeparatorItem(String sectionName) {
        mSectionName = sectionName;
    }

    @Override
    @Nonnull
    public ItemType getBaseType() {
        if (mSectionName.length() == 0) {
            return ItemType.IVT_SEPARATOR_EMPTY;
        } else {
            return ItemType.IVT_SEPARATOR_TEXT;
        }
    }

    @Override
    @Nonnull
    public String getText() {
        return mSectionName;
    }

    @Override
    @Nonnull
    public String getSectionName() {
        return mSectionName;
    }
}
