/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.browse.common.IconRetriever;
import com.orangebikelabs.orangesqueeze.browse.common.StandardIconRetriever;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;

import javax.annotation.Nonnull;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class PlaylistItemPre76 extends PlaylistItem {
    public PlaylistItemPre76(JsonNode json, MenuElement elem) {
        super(json, elem);
    }

    @Nonnull
    @Override
    public ImmutableList<IconRetriever> getIconRetrieverList() {
        // @formatter:off
        return ImmutableList.<IconRetriever>builder().
                addAll(super.getIconRetrieverList()).
                add(new StandardIconRetriever("id", ArtworkType.ALBUM_THUMBNAIL, false)).
                build();
        // @formatter:on
    }
}
