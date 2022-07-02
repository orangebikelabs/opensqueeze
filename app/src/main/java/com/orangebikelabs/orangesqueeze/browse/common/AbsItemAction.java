/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import android.content.Context;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.fasterxml.jackson.databind.JsonNode;
import com.orangebikelabs.orangesqueeze.actions.AbsAction;
import com.orangebikelabs.orangesqueeze.download.DownloadAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author tbsandee@orangebikelabs.com
 */
public abstract class AbsItemAction extends AbsAction<Item> {
    static public List<AbsItemAction> getContextActionCandidates(Context context) {
        List<AbsItemAction> list = new ArrayList<>();

        list.add(new PlayNowAction(context));
        list.add(new AddToPlaylistAction(context));
        list.add(new PlayNextAction(context));
        list.add(new DownloadAction(context));

        return list;
    }

    final protected Map<String, String> mItemMap = new HashMap<>();

    protected AbsItemAction(Context context, @StringRes int menuRid, @DrawableRes int iconRid) {
        super(menuRid == 0 ? "" : context.getString(menuRid), iconRid);
    }

    @Override
    public boolean initialize(Item item) {
        Iterator<String> it = item.getNode().fieldNames();
        while (it.hasNext()) {
            String key = it.next();
            JsonNode val = item.getNode().get(key);
            if (val != null && val.isValueNode()) {
                mItemMap.put(key, val.asText());
            }
        }
        return false;
    }
}
