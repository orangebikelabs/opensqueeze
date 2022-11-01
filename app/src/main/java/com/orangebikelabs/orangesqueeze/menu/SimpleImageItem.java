/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.browse.common.IconRetriever;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;
import com.orangebikelabs.orangesqueeze.browse.common.StandardIconRetriever;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import arrow.core.Option;

/**
 * Image item from slideshows.
 */
public class SimpleImageItem extends StandardMenuItem {
    static public boolean isSimpleImageItem(JsonNode node) {
        return node.has("image") && (node.has("caption") || node.has("name"));
    }


    final static private IconRetriever sIconRetriever = new StandardIconRetriever("image", ArtworkType.SERVER_RESOURCE_THUMBNAIL, true);
    final static private ImmutableList<IconRetriever> sIconRetrievers = ImmutableList.of(sIconRetriever);

    @Nonnull
    final private String mName;

    @Nullable
    final private String mDate;

    @Nullable
    final private String mOwner;

    @Nullable
    final private String mImage;

    public SimpleImageItem(JsonNode json, MenuElement element) {
        super(json, element, false);

        if (json.has("caption")) {
            mName = json.path("caption").asText();
        } else {
            mName = json.path("name").asText();
        }

        mOwner = parseField(json, "owner");
        mDate = parseField(json, "date");
        mImage = parseField(json, "image");
    }

    @Nullable
    private String parseField(JsonNode json, String field) {
        String retval = null;
        JsonNode node = json.path(field);
        if (node != null) {
            retval = node.asText();
            if (retval.equals("null")) {
                retval = null;
            } else if (retval.equals("")) {
                retval = null;
            }
        }
        return retval;
    }

    @Nonnull
    @Override
    public String getText() {
        return mName;
    }

    @Nonnull
    @Override
    public String getText1() {
        return mName;
    }

    @Nonnull
    @Override
    public Option<String> getText2() {
        return Option.fromNullable(mOwner);
    }

    @Nonnull
    @Override
    public Option<String> getText3() {
        return Option.fromNullable(mDate);
    }

    @Nonnull
    public Option<String> getImageUrl() {
        return Option.fromNullable(mImage);
    }

    @Override
    public boolean isEnabled() {
        return mImage != null;
    }

    @Nonnull
    @Override
    public synchronized ItemType getBaseType() {
        if (mOwner != null || mDate != null) {
            return ItemType.IVT_THUMBTEXT2;
        } else {
            return ItemType.IVT_THUMBTEXT;
        }
    }

    @Nonnull
    @Override
    public ImmutableList<IconRetriever> getIconRetrieverList() {
        return sIconRetrievers;
    }
}
