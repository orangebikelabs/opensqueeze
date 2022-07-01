/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.orangebikelabs.orangesqueeze.R;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
public class Drawables {

    /**
     * @return the drawable to be used for no artwork
     */
    @Nonnull
    static public Drawable getNoArtworkDrawable(Context context) {
        Drawable d = ContextCompat.getDrawable(context, R.drawable.artwork_missing);
        OSAssert.assertNotNull(d, "drawable not null");
        return d;
    }

    /**
     * @return the drawable to be used for no artwork
     */
    @Nonnull
    static public Drawable getNoArtworkDrawableTinted(Context context) {
        Drawable d = ContextCompat.getDrawable(context, R.drawable.artwork_missing);
        OSAssert.assertNotNull(d, "drawable not null");
        return getTintedDrawable(context, d);
    }

    @Nonnull
    static public Drawable getLoadingDrawable(Context context) {
        Drawable d = ContextCompat.getDrawable(context, R.drawable.artwork_loading);
        OSAssert.assertNotNull(d, "drawable not null");
        return d;
    }

    @Nonnull
    static public Drawable getTintedDrawable(Context context, Drawable drawable) {
        TypedArray a = context.obtainStyledAttributes(new int[]{R.attr.colorControlNormal});
        final int color = a.getColor(0, 0);
        a.recycle();
        Drawable newDrawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(newDrawable.mutate(), color);
        return drawable;
    }

    private Drawables() {
    }
}
