/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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
