/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import java.io.Serializable;

import javax.annotation.Nonnull;

/**
 * Used to hold a MenuElement for use in a ChoiceDialogFragment.
 *
 * @author tsandee
 */
public class MenuElementHolder implements Serializable {
    final String mText;
    final MenuElement mElement;

    public MenuElementHolder(String text, MenuElement element) {
        mText = text;
        mElement = element;
    }

    @Override
    @Nonnull
    public String toString() {
        return mText;
    }
}
