/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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