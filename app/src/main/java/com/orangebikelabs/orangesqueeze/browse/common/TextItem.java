/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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