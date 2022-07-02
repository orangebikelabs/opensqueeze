/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.common.base.Objects;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.browse.BrowseItemBaseAdapter;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;

import java.util.Locale;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class MenuListAdapter extends BrowseItemBaseAdapter {
    public MenuListAdapter(Context context, ThumbnailProcessor processor) {
        super(context, processor);
    }

    @Override
    protected boolean bindActionButton(StandardMenuItem item, View actionButton) {
        return item.getMenuElement().canShowContextMenu();
    }

    @Override
    protected void bindText1(StandardMenuItem item, TextView text1) {
        if (getItemType(item) == ItemType.IVT_SLIDER) {
            final int displayValue = item.getMutatedSliderValue() + item.getMenuElement().getSliderMinValue();
            String iconStyle = item.getMenuElement().getSliderIcons();
            if (Objects.equal(iconStyle, "volume")) {
                iconStyle = mContext.getString(R.string.menu_volume_slider_label, displayValue);
            } else if (iconStyle == null) {
                iconStyle = mContext.getString(R.string.menu_generic_slider_label, displayValue);
            }
            text1.setText(iconStyle);
        } else {
            text1.setText(item.getText1());
        }
    }

    @Override
    protected void bindSlider(StandardMenuItem item, SeekBar seek) {
        super.bindSlider(item, seek);
    }

    @Override
    protected void bindText2(StandardMenuItem item, TextView text2) {
        if (getItemType(item) == ItemType.IVT_SLIDER) {
            text2.setText(String.format(Locale.getDefault(), "%d", item.getMenuElement().getSliderMinValue()));
        } else {
            String text = item.getText2().orNull();
            if (text == null) {
                text2.setVisibility(View.GONE);
            } else {
                text2.setVisibility(View.VISIBLE);
                text2.setText(text);
            }
        }
    }

    @Override
    protected void bindText3(StandardMenuItem item, TextView text3) {
        if (getItemType(item) == ItemType.IVT_SLIDER) {
            text3.setText(String.format(Locale.getDefault(), "%d", item.getMenuElement().getSliderMaxValue()));
        } else {
            String text = item.getText3().orNull();
            if (text == null) {
                text3.setVisibility(View.GONE);
            } else {
                text3.setVisibility(View.VISIBLE);
                text3.setText(text);
            }
        }
    }

    @Override
    protected void bindCheckbox(StandardMenuItem item, CheckBox checkbox) {
        MenuElement menuElement = item.getMenuElement();
        checkbox.setText(item.getItemTitle());

        Boolean mutatedState = item.getMutatedCheckboxChecked();
        if (mutatedState != null) {
            checkbox.setChecked(mutatedState);
        } else {
            Integer checkedValue = menuElement.getCheckbox();
            checkbox.setChecked(checkedValue != null && checkedValue != 0);
        }
    }
    //	@Override
    //	protected List<SortBy> initSortByOptions() {
    //		return Arrays.asList(SortBy.YEAR_ASC, SortBy.YEAR_DESC);
    //	}
    //
    //	@Override
    //	public void onPrepareTipList(List<TipInfo> tipList) {
    //		super.onPrepareTipList(tipList);
    //
    //		tipList.add(new TipInfo(YearListAdapter.class, "sortorder", R.string.tips_year_sortorder, 1));
    //	}

}
