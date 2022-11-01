/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse;

import android.os.Parcel;
import android.os.Parcelable;

import com.orangebikelabs.orangesqueeze.R;

/**
 * @author tsandee
 */
public enum BrowseStyle implements Parcelable {

    // @formatter:off

    // standard list
    LIST(R.layout.browseview_list),

    // slightly enlarged artist artwork
    ARTIST_LIST(R.layout.browseview_list),

    // all grids the same size
    GRID(R.layout.browseview_grid),

    // all coverflow is the same size
    COVERFLOW(R.layout.browseview_coverflow),

    // root menu w/drag n drop is the same
    ROOT_DRAGDROP(R.layout.browseview_dragdrop),

    // left drawer
    BROWSE_DRAWER(R.layout.browsedrawer_list);

    // @formatter:on

    public static final Parcelable.Creator<BrowseStyle> CREATOR = new Parcelable.Creator<>() {
        @Override
        public BrowseStyle createFromParcel(Parcel in) {
            return BrowseStyle.values()[in.readInt()];
        }

        @Override
        public BrowseStyle[] newArray(int size) {
            return new BrowseStyle[size];
        }
    };

    final private int mLayoutId;

    BrowseStyle(int layoutId) {
        mLayoutId = layoutId;
    }

    public int getLayoutId() {
        return mLayoutId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }
}
