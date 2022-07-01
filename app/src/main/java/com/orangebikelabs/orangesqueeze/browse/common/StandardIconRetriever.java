/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.google.common.base.Strings;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class StandardIconRetriever extends IconRetriever {

    final public String mKey;
    final public ArtworkType mType;
    final public ScaleType mScaleType;
    final public boolean mApplyToAll;

    public StandardIconRetriever(String key, ArtworkType type, boolean applyToAll) {
        this(key, type, ScaleType.CENTER, applyToAll);
    }

    public StandardIconRetriever(String key, ArtworkType type, ScaleType scaleType, boolean applyToAll) {
        mKey = key;
        mType = type;
        mScaleType = scaleType;
        mApplyToAll = applyToAll;
    }

    @Override
    public boolean applies(Item item) {
        return mApplyToAll || item.getNode().has(mKey);
    }

    @Override
    public boolean load(ThumbnailProcessor processor, Item item, AbsListView parentView, @Nullable ImageView imageView) {
        String coverId = getCover(item);

        if (coverId != null) {
            addArtworkJob(processor, imageView, coverId, mType, mScaleType);
        } else {
            setNoArtwork(processor, imageView);
        }
        return true;
    }

    @Nullable
    private String getCover(Item item) {
        String cover = item.getNode().path(mKey).asText();
        return Strings.emptyToNull(cover);
    }
}
