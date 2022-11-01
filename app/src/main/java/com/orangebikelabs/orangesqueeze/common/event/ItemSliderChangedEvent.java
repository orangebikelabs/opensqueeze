/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;

import com.google.android.material.slider.Slider;
import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
@Keep
public class ItemSliderChangedEvent extends AbsViewEvent {
    @Nonnull
    final private StandardMenuItem mItem;

    final private int mNewValue;

    public ItemSliderChangedEvent(Slider slider, StandardMenuItem item, int newValue) {
        super(slider);

        mItem = item;
        mNewValue = newValue;
    }

    @Nonnull
    public StandardMenuItem getItem() {
        return mItem;
    }

    public int getNewValue() { return mNewValue; }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("newValue", mNewValue).add("item", mItem).toString();
    }
}
