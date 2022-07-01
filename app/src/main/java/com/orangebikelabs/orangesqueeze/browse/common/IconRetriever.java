/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import android.graphics.drawable.Drawable;
import android.widget.AbsListView;
import android.widget.ImageView;

import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.artwork.RecyclableBitmap;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.common.Drawables;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
abstract public class IconRetriever {
    abstract public boolean applies(Item item);

    abstract public boolean load(ThumbnailProcessor processor, Item item, AbsListView parentView, @Nullable ImageView imageView);

    protected void addArtworkJob(ThumbnailProcessor processor, @Nullable ImageView imageView, String coverId,
                                 ArtworkType artworkType, ImageView.ScaleType scaleType) {
        if (imageView != null) {
            processor.addArtworkJob(imageView, coverId, artworkType, scaleType);
        } else {
            processor.addArtworkPreloadJob(coverId, artworkType);
        }
    }

    protected void setArtwork(ThumbnailProcessor processor, @Nullable ImageView imageView, Drawable d, ImageView.ScaleType scaleType) {
        if (imageView != null) {
            Drawable newDrawable = Drawables.getTintedDrawable(imageView.getContext(), d);
            processor.setArtwork(imageView, newDrawable, scaleType);
        }
    }

    protected void setArtwork(ThumbnailProcessor processor, @Nullable ImageView imageView, RecyclableBitmap bmp, ImageView.ScaleType scaleType) {
        if (imageView != null) {
            processor.setArtwork(imageView, bmp, scaleType);
        }
    }

    protected void setNoArtwork(ThumbnailProcessor processor, @Nullable ImageView imageView) {
        if (imageView != null) {
            processor.setNoArtwork(imageView);
        }
    }
}
