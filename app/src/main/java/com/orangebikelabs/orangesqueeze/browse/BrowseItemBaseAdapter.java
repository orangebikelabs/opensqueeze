/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse;

import android.content.Context;
import androidx.annotation.Keep;
import android.view.View;

import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.browse.common.AbsItemAction;
import com.orangebikelabs.orangesqueeze.browse.common.ItemBaseAdapter;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@NotThreadSafe
@Keep
abstract public class BrowseItemBaseAdapter extends ItemBaseAdapter implements OSBrowseAdapter {
    @Nonnull
    final protected List<AbsItemAction> mActionCandidates;

    protected BrowseItemBaseAdapter(Context context, ThumbnailProcessor processor) {
        super(context, processor);

        mActionCandidates = AbsItemAction.getContextActionCandidates(context);
    }

    @Override
    protected boolean bindActionButton(StandardMenuItem item, View actionButton) {
        for (AbsItemAction c : mActionCandidates) {
            if (c.initialize(item)) {
                return true;
            }
        }
        return false;
    }
}
