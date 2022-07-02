/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import android.view.View;
import android.widget.ProgressBar;

import com.orangebikelabs.orangesqueeze.actions.AbsAction;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.download.DownloadAction;

import java.util.List;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class ItemContextMenuRequest extends ContextMenuRequest {

    /**
     * used to alter the progress state of the item
     */
    final private StandardMenuItem mItem;

    @Nullable
    final private ProgressBar mProgressBar;

    public ItemContextMenuRequest(AbsMenuFragment controller, View itemView, StandardMenuItem item) {
        super(controller, itemView, item, item.getItemTitle());

        mItem = item;

        if (itemView instanceof ProgressBar) {
            mProgressBar = (ProgressBar) itemView;
        } else {
            mProgressBar = null;
        }

        OSAssert.assertMainThread();
    }

    protected void setProgressVisible(boolean visible) {
        mItem.setMutatedProgressVisible(visible, mProgressBar);
    }

    @Override
    protected void initializeRequest() throws SBRequestException {
        super.initializeRequest();

        OSExecutors.getMainThreadExecutor().execute(() -> setProgressVisible(true));
    }

    @Override
    protected void finalizeRequest() throws SBRequestException {
        super.finalizeRequest();

        OSExecutors.getMainThreadExecutor().execute(() -> setProgressVisible(false));
    }

    @Override
    protected void abortRequest() throws SBRequestException {
        super.abortRequest();

        OSExecutors.getMainThreadExecutor().execute(() -> setProgressVisible(false));
    }

    @Override
    protected void onPopulateActionList(List<AbsAction<Item>> actionList) {
        super.onPopulateActionList(actionList);

        actionList.add(new DownloadAction(mContext));
    }
}
