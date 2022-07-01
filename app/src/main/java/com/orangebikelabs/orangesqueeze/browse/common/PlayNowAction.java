/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import android.content.Context;

import androidx.fragment.app.Fragment;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.Futures;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.AbsFragmentResultReceiver;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.menu.AbsMenuFragment;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class PlayNowAction extends AbsItemAction {
    protected String mItemTitle;
    protected String mFilterParam;

    public PlayNowAction(Context context) {
        this(context, R.string.play_desc, R.drawable.ic_play_arrow);
    }

    protected PlayNowAction(Context context, int menuRid, int iconRid) {
        super(context, menuRid, iconRid);
    }

    @Override
    public boolean initialize(Item item) {
        super.initialize(item);

        mItemTitle = item.getItemTitle();
        mFilterParam = null;

        JsonNode node = item.getNode();
        if (node.has("contributor_id")) {
            mFilterParam = "artist_id:" + node.get("contributor_id").asText();
        } else if (node.has("album_id")) {
            mFilterParam = "album_id:" + node.get("album_id").asText();
        } else if (node.has("track_id")) {
            mFilterParam = "track_id:" + node.get("track_id").asText();
        }
        return true;
    }

    protected String getPlayCommand() {
        return "load";
    }

    @Override
    public boolean execute(Fragment controller) {
        OSAssert.assertMainThread();

        if (mFilterParam != null) {

            SBContext context = SBContextProvider.get();

            SBRequest request = context.newRequest("playlistcontrol", "cmd:" + getPlayCommand(), mFilterParam);
            CharSequence toastMessage = CommandTools.lookupToast(controller.requireContext(), request, mItemTitle);

            request.setPlayerId(context.getPlayerId());
            FutureResult result = request.submit(OSExecutors.getUnboundedPool());
            if (toastMessage != null) {
                Futures.addCallback(result, new PlayCommandResultReceiver((AbsMenuFragment) controller, toastMessage), OSExecutors.getMainThreadExecutor());
            }
        }
        return true;
    }


    static class PlayCommandResultReceiver extends AbsFragmentResultReceiver<AbsMenuFragment> {

        @Nullable
        final private CharSequence mToastMessage;

        PlayCommandResultReceiver(AbsMenuFragment fragment, @Nullable CharSequence toastMessage) {
            super(fragment);

            mToastMessage = toastMessage;
        }

        @Override
        public void onEventualSuccess(AbsMenuFragment fragment, SBResult result) {
            if (mToastMessage != null) {
                fragment.showSnackbar(mToastMessage, AbsMenuFragment.SnackbarLength.LONG);
            }
        }
    }
}
