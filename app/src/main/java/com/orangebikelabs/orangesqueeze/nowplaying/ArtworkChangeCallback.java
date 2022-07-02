/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
