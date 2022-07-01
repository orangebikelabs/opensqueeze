/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
