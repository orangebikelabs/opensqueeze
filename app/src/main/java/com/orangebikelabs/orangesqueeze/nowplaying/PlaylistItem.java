/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView.ScaleType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.browse.common.IconRetriever;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;
import com.orangebikelabs.orangesqueeze.browse.common.StandardIconRetriever;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.menu.AbsMenuFragment;
import com.orangebikelabs.orangesqueeze.menu.ItemContextMenuRequest;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import javax.annotation.Nonnull;

import arrow.core.Option;
import arrow.core.OptionKt;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class PlaylistItem extends StandardMenuItem {
    final private static String PLAYLIST_INDEX_NODE = "playlist index";
    final private static String PLAYLIST_INDEX_ITEM = "playlist_index";

    // @formatter:off
    final static private ImmutableList<IconRetriever> sIconRetrieverList =
            ImmutableList.<IconRetriever>builder()
                    .add(new StandardIconRetriever("artwork_url", ArtworkType.SERVER_RESOURCE_THUMBNAIL, ScaleType.CENTER_INSIDE, false))
                    .add(new StandardIconRetriever("coverid", ArtworkType.ALBUM_THUMBNAIL, false))
                    .build();
    // @formatter:on

    public PlaylistItem(JsonNode json, MenuElement elem) {
        super(json, elem, false);
    }

    @Nonnull
    @Override
    public ImmutableList<IconRetriever> getIconRetrieverList() {
        return sIconRetrieverList;
    }

    @Nonnull
    @Override
    public String getText1() {
        String artist = Strings.emptyToNull(mJson.path("artist").asText());
        String title = Strings.emptyToNull(mJson.path("title").asText());
        int tracknum = Integer.MIN_VALUE;
        if (mJson.has("tracknum")) {
            tracknum = mJson.get("tracknum").asInt();
        }

        StringBuilder builder = new StringBuilder(100);
        builder.setLength(0);
        if (tracknum != Integer.MIN_VALUE) {
            builder.append(tracknum);
            builder.append(". ");
        }

        if (title != null) {
            builder.append(title);
        }

        if (artist != null) {
            builder.append(" (");
            builder.append(artist);
            builder.append(")");
        }
        return builder.toString();
    }

    @Nonnull
    @Override
    public Option<String> getText2() {

        StringBuilder builder = new StringBuilder(100);

        String year = Strings.emptyToNull(mJson.path("year").asText());
        String album = Strings.emptyToNull(mJson.path("album").asText());

        if (Objects.equal(year, "0")) {
            year = null;
        }

        if (album != null) {
            builder.append(album);
        }

        if (year != null) {
            builder.append(" (");
            builder.append(year);
            builder.append(")");
        }
        if (builder.length() > 0) {
            return OptionKt.some(builder.toString());
        } else {
            return OptionKt.none();
        }
    }

    @Nonnull
    @Override
    public Option<String> getText3() {
        int duration = mJson.path("duration").asInt();
        if (duration > 0) {
            return OptionKt.some(DateUtils.formatElapsedTime(duration));
        } else {
            return OptionKt.none();
        }
    }

    @Override
    public boolean showContextMenu(AbsMenuFragment fragment, View progressBar) {
        OSAssert.assertMainThread();

        ItemContextMenuRequest request = new ItemContextMenuRequest(fragment, progressBar, this);
        request.setCommands("contextmenu");
        String playlistIndex = PLAYLIST_INDEX_ITEM + ":" + mJson.path(PLAYLIST_INDEX_NODE).asText();
        request.setParameters(playlistIndex, "menu:track", "context:playlist", "useContextMenu:1");
        request.submit(OSExecutors.getUnboundedPool());
        return true;
    }

    @Nonnull
    @Override
    public ItemType getBaseType() {
        return ItemType.IVT_NOWPLAYING_PLAYLIST_ITEM;
    }

    @Override
    public long getAdapterItemId() {
        long retval;
        if (mJson.has(PLAYLIST_INDEX_NODE)) {
            int playlistIndex = mJson.get(PLAYLIST_INDEX_NODE).asInt();
            int secondary = mJson.path("id").asInt(-1);
            if (secondary == -1) {
                secondary = getText().hashCode();
            }
            retval = (((long) secondary) << 32) | (playlistIndex & 0xffffffffL);
        } else {
            retval = super.getAdapterItemId();
        }
        return retval;
    }
}
