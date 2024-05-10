/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.content.Context;
import android.content.Intent;
import androidx.fragment.app.Fragment;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.browse.common.AbsItemAction;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.menu.ActionNames;
import com.orangebikelabs.orangesqueeze.menu.MenuAction;
import com.orangebikelabs.orangesqueeze.menu.MenuHelpers;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.util.Collections;
import java.util.List;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class DownloadAction extends AbsItemAction {
    private List<String> mCommandList;
    private List<String> mParamList;
    private String mDownloadTitle;

    public DownloadAction(Context context) {
        super(context, R.string.download_desc, R.drawable.ic_download);
    }

    @Override
    public boolean initialize(Item item) {
        boolean retval = false;
        if (item instanceof StandardMenuItem smi) {
            MenuAction goAction = MenuHelpers.getAction(smi.getMenuElement(), ActionNames.GO);
            if (goAction != null) {
                mDownloadTitle = smi.getItemTitle();
                if (smi.getMenuElement().isTrack()) {
                    mCommandList = Collections.singletonList("titles");
                    mParamList = Collections.singletonList("track_id:" + smi.getMenuElement().getTrackId());
                    retval = true;
                } else if (DownloadHelper.isNestableRequest(goAction.getCommands())) {
                    mCommandList = goAction.getCommands();
                    mParamList = MenuHelpers.buildParametersAsList(smi.getMenuElement(), goAction, false);
                    retval = mParamList != null;
                }
            }
        }
        return retval;
    }

    @Override
    public boolean execute(Fragment controller) {
        Intent intent = PrepareDownloadActivity.Companion.newIntent(controller.requireContext(), mCommandList, mParamList, mDownloadTitle);
        controller.startActivity(intent);
        return true;
    }
}
