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

    @Nonnull
    final private Slider mSlider;

    public ItemSliderChangedEvent(Slider slider, StandardMenuItem item) {
        super(slider);

        mSlider = slider;
        mItem = item;
    }

    @Nonnull
    public StandardMenuItem getItem() {
        return mItem;
    }

    @Nonnull
    public Slider getSlider() {
        return mSlider;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("item", mItem).toString();
    }
}
