/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
