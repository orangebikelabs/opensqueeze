/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.util.DisplayMetrics;

import com.orangebikelabs.orangesqueeze.common.SBContextProvider;

/**
 * Artwork factory
 */
public class ArtworkFactory {
    private ArtworkFactory() {
        // no instances
    }

    // TODO convert to lazy init
    public static int getDisplayArtworkWidth() {
        DisplayMetrics dm = SBContextProvider.get().getApplicationContext().getResources().getDisplayMetrics();

        return Math.max(dm.heightPixels, dm.widthPixels);
    }
}
