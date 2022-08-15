/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.common.Drawables;
import com.orangebikelabs.orangesqueeze.common.NavigationCommandSet;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.SBResult;

import java.util.ArrayList;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class RandomPlayListAdapter extends ArrayAdapter<Item> {
    final private RPGenreListItem mGenreItem;

    public RandomPlayListAdapter(Context context) {
        super(context, R.layout.browseitem_thumb, R.id.text1);

        SBContext sbContext = SBContextProvider.get();

        int sort = 0;
        add(new RPActionItem(context, R.string.randomplay_track, R.drawable.ic_shuffle, "tracks", sort++));
        add(new RPActionItem(context, R.string.randomplay_artist, R.drawable.ic_artist, "contributors", sort++));
        add(new RPActionItem(context, R.string.randomplay_album, R.drawable.ic_album, "albums", sort++));
        add(new RPActionItem(context, R.string.randomplay_year, R.drawable.ic_year, "year", sort++));

        mGenreItem = new RPGenreListItem(context, sort++);
        add(mGenreItem);

        SBRequest request = sbContext.newRequest(SBRequest.Type.COMET, "randomplaygenrelist", "0", "10000");
        request.setPlayerId(sbContext.getPlayerId());
        FutureResult result = request.submit(OSExecutors.getUnboundedPool());
        Futures.addCallback(result, mResultCallback, OSExecutors.getMainThreadExecutor());
    }

    public void setGenreCount(int count) {
        mGenreItem.setGenreCount(count);
        notifyDataSetChanged();
    }

    @Nonnull
    @Override
    public Item getItem(int position) {
        return super.getItem(position);
    }

    @Override
    @Nonnull
    public View getView(int position, @Nullable View convertView, ViewGroup parent) {
        View retval = super.getView(position, convertView, parent);

        RPActionItem item = (RPActionItem) getItem(position);

        View actionView = retval.findViewById(R.id.action_button);
        if (actionView != null) {
            actionView.setVisibility(View.GONE);
        }
        ImageView iconView = retval.findViewById(R.id.icon);
        if (iconView != null) {
            iconView.setScaleType(ScaleType.CENTER_CROP);
            iconView.setContentDescription(item.getText());

            Drawable d = ContextCompat.getDrawable(iconView.getContext(), item.getIconRid());
            OSAssert.assertNotNull(d, "not null");

            Drawable newDrawable = Drawables.getTintedDrawable(iconView.getContext(), d);
            iconView.setImageDrawable(newDrawable);
        }
        return retval;
    }

    static public class RPActionItem extends ActionItem {
        final protected String mRandomPlayMode;

        @DrawableRes
        final protected int mIconRid;

        public RPActionItem(Context context, @StringRes int actionStringResId, @DrawableRes int iconRid, @Nullable String randomPlayMode, int sortOrder) {
            super(context, actionStringResId, sortOrder);
            mRandomPlayMode = randomPlayMode;
            mIconRid = iconRid;
        }

        @DrawableRes
        @Override
        public int getIconRid() {
            return mIconRid;
        }

        public String getRandomPlayMode() {
            return mRandomPlayMode;
        }

        public void click(BrowseRequestFragment browseFragment) {

            //	String toast = browseFragment.getString(R.string.playnow_action_toast_html, toString());

            // set to play now so that we jump to now playing
            //browseFragment.executePlayAction(PlayNowAction.PLAY_NOW_COMMAND, FilterByType.RANDOMPLAY_MODE, getRandomPlayMode(), Html.fromHtml(toast));
        }
    }

    static public class RPGenreListItem extends RPActionItem {

        private String mGenreListString;
        final private Context mContext;

        public RPGenreListItem(Context context, int sortOrder) {
            super(context, R.string.randomplay_genreselect_loading, R.drawable.ic_genre, null, sortOrder);

            mContext = context;
        }

        protected void setGenreCount(int count) {
            mGenreListString = mContext.getString(R.string.randomplay_genreselect, count);
        }

        @Override
        public void click(BrowseRequestFragment browseFragment) {
            NavigationCommandSet commandSet = new NavigationCommandSet(Collections.singletonList("randomplaygenrelist"), new ArrayList<>());
            browseFragment.executeMenuBrowse(toString(), commandSet, null, null, null);
        }

        @Nonnull
        @Override
        public String toString() {
            if (mGenreListString == null) {
                return super.toString();
            } else {
                return mGenreListString;
            }
        }
    }

    final private FutureCallback<SBResult> mResultCallback = new FutureCallback<>() {

        @Override
        public void onSuccess(@Nullable SBResult result) {
            OSAssert.assertNotNull(result, "can't be null");

            OSLog.jsonTrace("Genres", result.getJsonResult());

            int activeGenres = 0;
            JsonNode items = result.getJsonResult().path("item_loop");
            for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                if (item.path("checkbox").asInt() != 0) {
                    activeGenres++;
                }
            }
            setGenreCount(activeGenres);
        }

        @Override
        public void onFailure(Throwable e) {
            OSLog.w(e.getMessage(), e);
        }
    };
}
