/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.actions;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.Drawables;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import androidx.core.content.ContextCompat;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class ActionListAdapter<T> extends ArrayAdapter<AbsAction<T>> {

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        AbsAction<T> item = getItem(position);
        return item.isEnabled();
    }

    public ActionListAdapter(Context context, List<AbsAction<T>> actions) {
        super(context, R.layout.actionmenu_item, actions);
    }

    @Nonnull
    @Override
    public AbsAction<T> getItem(int position) {
        //noinspection ConstantConditions
        return super.getItem(position);
    }

    @Override
    @Nonnull
    public View getView(int position, @Nullable View convertView, ViewGroup parent) {
        TextView tv = (TextView) super.getView(position, convertView, parent);
        AbsAction<?> ac = getItem(position);
        tv.setCompoundDrawablePadding(getContext().getResources().getDimensionPixelSize(R.dimen.actionitem_icon_padding));
        tv.setTag(R.id.tag_action, ac);
        Drawable d = null;
        if (ac.getIconRid() != 0) {
            d = ContextCompat.getDrawable(getContext(), ac.getIconRid());
        }
        if (d == null) {
            d = ContextCompat.getDrawable(getContext(), R.drawable.ic_info_outline);
        }
        if (d != null) {
            d = Drawables.getTintedDrawable(getContext(), d);
            int height = getContext().getResources().getDimensionPixelSize(R.dimen.actionitem_height);
            int inset = getContext().getResources().getDimensionPixelOffset(R.dimen.actionitem_icon_padding);
            Rect rect = new Rect(0, 0, height - inset * 2, height - inset * 2);
            d.setBounds(rect);
        }
        tv.setCompoundDrawables(d, null, null, null);
        return tv;
    }
}
