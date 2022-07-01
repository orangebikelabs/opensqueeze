/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common.event;

import androidx.annotation.Keep;
import android.view.View;

import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
@Keep
public class ItemActionButtonClickEvent extends AbsViewEvent {

    @Nonnull
    final private View mActionButtonView;

    @Nonnull
    final private StandardMenuItem mItem;

    final private int mPosition;

    public ItemActionButtonClickEvent(View actionButtonView, StandardMenuItem item, int position) {
        super(actionButtonView);
        mActionButtonView = actionButtonView;
        mItem = item;
        mPosition = position;
    }

    @Nonnull
    public StandardMenuItem getItem() {
        return mItem;
    }

    public int getPosition() {
        return mPosition;
    }

    @Nonnull
    public View getActionButtonView() {
        return mActionButtonView;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).add("item", mItem).add("position", mPosition).toString();
    }
}
