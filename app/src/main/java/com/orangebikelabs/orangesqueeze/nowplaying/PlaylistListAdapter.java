/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.browse.common.ItemBaseAdapter;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Optional;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class PlaylistListAdapter extends ItemBaseAdapter {
    @Nullable
    private String mPlaylistTimestamp;

    private int mPlaylistIndex = AdapterView.INVALID_POSITION;

    public PlaylistListAdapter(Context context, ThumbnailProcessor processor) {
        super(context, processor);
    }

    @Override
    public void swapItems(int i1, int i2) {
        super.swapItems(i1, i2);

        // the current playlist index moves as we swap
        if(mPlaylistIndex == i1) {
            mPlaylistIndex = i2;
        } else if(mPlaylistIndex == i2) {
            mPlaylistIndex = i1;
        }
    }

    @Override
    public void remove(int position) {
        if (position < mPlaylistIndex) {
            mPlaylistIndex--;
        }

        super.remove(position);
    }

    public void setPlaylistTimestamp(@Nullable String timestamp) {
        mPlaylistTimestamp = timestamp;
    }

    @Nonnull
    public Optional<String> getPlaylistTimestamp() {
        return Optional.ofNullable(mPlaylistTimestamp);
    }

    public int getPlaylistIndex() {
        OSAssert.assertMainThread();

        if (mPlaylistIndex >= getCount()) {
            Reporting.report("Playlist index out of range (count=" + getCount() + ", ndx=" + mPlaylistIndex);
            mPlaylistIndex = AdapterView.INVALID_POSITION;
            if (mNotifyOnChange) {
                notifyDataSetChanged();
            }
        }
        return mPlaylistIndex;
    }

    public void setPlaylistIndex(int index) {
        OSAssert.assertMainThread();

        if (mPlaylistIndex != index) {
            if(index < 0 || index >= getCount()) {
                mPlaylistIndex = AdapterView.INVALID_POSITION;
            } else {
                mPlaylistIndex = index;
            }
            if (mNotifyOnChange) {
                notifyDataSetChanged();
            }
        }
    }

    @Override
    public void clear() {
        OSAssert.assertMainThread();

        mPlaylistIndex = AdapterView.INVALID_POSITION;
        mPlaylistTimestamp = null;

        super.clear();
    }

    @Override
    protected boolean bindActionButton(StandardMenuItem item, View actionButton) {
        return true;
    }

    @Override
    public View getView(int position, @Nullable View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        if (view != null) {
            view.setBackgroundResource(0);

            // if background isn't set....
            if (view.getBackground() == null && position == mPlaylistIndex) {
                view.setBackgroundResource(R.drawable.playlist_item_selected);
            }
        }

        return view;
    }

    @Override
    @Nonnull
    protected IconVisibility bindIcon(StandardMenuItem item, ViewGroup parentView, ImageView icon) {
        IconVisibility retval = super.bindIcon(item, parentView, icon);
        if (retval != IconVisibility.VISIBLE) {
            // set placeholder no artwork in playlist
            mThumbnailProcessor.setNoArtwork(icon);
            retval = IconVisibility.VISIBLE;
        }
        return retval;
    }

    @Override
    protected void bindText1(StandardMenuItem item, TextView text1) {
        text1.setText(item.getText1());
    }

    @Override
    protected void bindText2(StandardMenuItem item, TextView text2) {
        String text = item.getText2().orElse("");
        text2.setText(text);
    }

    @Override
    protected void bindText3(StandardMenuItem item, TextView text3) {
        PlaylistItem pi = (PlaylistItem) item;
        String text = item.getText3().orElse("");
        text3.setText(text);
    }
}