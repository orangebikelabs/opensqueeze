/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import androidx.annotation.LayoutRes;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;

/**
 * @author tbsandee@orangebikelabs.com
 */
public enum ItemType {
    IVT_ACTION(R.layout.browseitem_thumb),
    IVT_THUMBTEXT(R.layout.browseitem_thumb, R.layout.browse_grid_thumb),
    IVT_THUMBTEXT2(R.layout.browseitem_thumb2, R.layout.browse_grid_thumb2),
    IVT_SEPARATOR_TEXT(R.layout.browseitem_separator, 0),
    IVT_SEPARATOR_EMPTY(R.layout.browseitem_separator_empty, 0),
    IVT_ARTIST(R.layout.browseitem_artistthumb, R.layout.browse_grid_thumb),
    IVT_YEAR(R.layout.browseitem_thumb, R.layout.browse_grid_year),
    IVT_PLAYER(R.layout.manageplayers_item, 0),
    IVT_CHECKBOX(R.layout.browseitem_checkbox),
    IVT_RADIO(R.layout.browseitem_radio),
    IVT_CHOICE(R.layout.browseitem_choice),
    IVT_SLIDER(R.layout.browseitem_slider),
    IVT_NOWPLAYING_PLAYLIST_ITEM(R.layout.nowplaying_playlist_item),
    IVT_TEXT(R.layout.browseitem_text, R.layout.browseitem_text),
    IVT_GLOBALSEARCH_HEADER(R.layout.globalsearch_separator),
    IVT_GLOBALSEARCH_OTHER_HEADER(R.layout.globalsearch_separator, 0),
    IVT_CUSTOMIZEROOT(R.layout.customizerootmenus_item, 0),
    IVT_LOADING(R.layout.browseitem_loading, R.layout.browse_grid_loading),
    IVT_DRAWERITEM(R.layout.browsedrawer_item, 0);

    @LayoutRes
    final private int mListLayoutRid;

    @LayoutRes
    final private int mGridLayoutRid;

    ItemType(@LayoutRes int layoutRid) {
        this(layoutRid, layoutRid);
    }

    ItemType(@LayoutRes int listLayoutRid, @LayoutRes int gridLayoutRid) {
        mListLayoutRid = listLayoutRid;
        mGridLayoutRid = gridLayoutRid;
    }

    @LayoutRes
    public int getListLayoutRid() {
        // override this layout to a shorter one if artwork preference is disabled
        if (this == IVT_ARTIST && SBPreferences.get().isArtistArtworkDisabled()) {
            return R.layout.browseitem_thumb;
        } else {
            return mListLayoutRid;
        }
    }

    @LayoutRes
    public int getGridLayoutRid() {
        return mGridLayoutRid;
    }
}
