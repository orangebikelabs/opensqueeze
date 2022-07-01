/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequest;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;

import java.io.IOException;
import java.util.Arrays;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class LegacySearchRequest extends BrowseRequest {
    public LegacySearchRequest(@Nullable PlayerId playerId, String searchTerm) {
        super(playerId);

        setCommands("search");
        addParameter("term", searchTerm);
        setLoopAndCountKeys(
                Arrays.asList("contributors_loop", "albums_loop", "tracks_loop"),
                Arrays.asList("contributors_count", "albums_count", "tracks_count"));
    }

    @Override
    protected void onFinishLoop(SBResult result) throws SBRequestException {
        // add section-level separators on each loop
        addSeparators();
    }

    @Override
    protected void onLoopItem(SBResult result, ObjectNode item) throws SBRequestException {
        // explicitly do NOT call through to BrowseRequest.onLoopItem()

        String sectionName = null;
        ItemType type = null;

        if (item.has("contributor")) {
            sectionName = mContext.getResources().getString(R.string.section_artist);
            type = ItemType.IVT_ARTIST;
        } else if (item.has("album")) {
            sectionName = mContext.getResources().getString(R.string.section_album);
            type = ItemType.IVT_THUMBTEXT2;
        } else if (item.has("track")) {
            sectionName = mContext.getResources().getString(R.string.section_track);
            type = ItemType.IVT_THUMBTEXT2;
        } else {
            // ignore other data items
        }

        if (sectionName != null) {
            try {
                MenuElement element = MenuElement.get(item, getMenuBase());
                mItemList.add(new LegacySearchItem(item, element, sectionName, type));
            } catch (IOException e) {
                Reporting.report(e, "Error handling menu item", item);
            }
        }
    }
}
