/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;
import android.widget.SeekBar;

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
    final private SeekBar mSeekBar;

    public ItemSliderChangedEvent(SeekBar seekBar, StandardMenuItem item) {
        super(seekBar);

        mSeekBar = seekBar;
        mItem = item;
    }

    @Nonnull
    public StandardMenuItem getItem() {
        return mItem;
    }

    @Nonnull
    public SeekBar getSeekBar() {
        return mSeekBar;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("item", mItem).toString();
    }
}
