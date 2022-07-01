/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import android.graphics.Bitmap;

import javax.annotation.Nullable;

/**
 * Implemented by activity to receive notifications about artwork updates.
 */
public interface ArtworkChangeCallback {
    void notifyNewArtwork(@Nullable Bitmap bmp, boolean loading);
}
