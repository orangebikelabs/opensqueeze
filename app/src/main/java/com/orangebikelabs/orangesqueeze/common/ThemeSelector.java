/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;
import androidx.annotation.StyleRes;

import com.orangebikelabs.orangesqueeze.R;

/**
 * Enumerates the themes we have available in the app. This class must be synchronized with the ID's in the preferences XML definitions.
 *
 * @author tbsandee@orangebikelabs.com
 */
@Keep
public enum ThemeSelector {
    NORMAL(R.style.Theme_OrangeSqueeze), COMPACT(R.style.Theme_OrangeSqueeze_Compact);

    @StyleRes
    final private int mRid;

    ThemeSelector(@StyleRes int primaryRid) {
        mRid = primaryRid;
    }

    @StyleRes
    public int getRid() {
        return mRid;
    }
}
