/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.browse.common.IconRetriever;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.browse.common.ItemBaseAdapter.ViewHolder;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;
import com.orangebikelabs.orangesqueeze.cache.CacheFuture;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.ListJobProcessor.Job;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.TrackInfo;

import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import java.util.Optional;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class TrackMenuItem extends StandardMenuItem {

    final static private ImmutableList<IconRetriever> sIconRetrievers;

    /**
     * weak keys
     */
    final static protected Map<TextView, TrackMenuItem> sLoadJobs = new WeakHashMap<>();

    final private static IconRetriever sTrackItemIconRetriever = new IconRetriever() {

        @Override
        public boolean applies(Item item) {
            TrackMenuItem tmi = (TrackMenuItem) item;
            return tmi.getCoverId().isPresent();
        }

        @Override
        public boolean load(ThumbnailProcessor processor, Item item, AbsListView parent, @Nullable ImageView iv) {
            TrackMenuItem tmi = (TrackMenuItem) item;
            String coverId = tmi.getCoverId().orElse(null);
            if (coverId == null) {
                return false;
            }

            addArtworkJob(processor, iv, coverId, ArtworkType.ALBUM_THUMBNAIL, ImageView.ScaleType.CENTER);
            return true;
        }
    };

    static {
        sIconRetrievers = ImmutableList.of(sTrackItemIconRetriever, MenuElement.newIconRetriever());
    }

    @GuardedBy("this")
    @Nonnull
    private String mText1;

    @GuardedBy("this")
    @Nullable
    private String mText2;

    @GuardedBy("this")
    @Nullable
    private String mText3;

    @GuardedBy("this")
    @Nullable
    private TrackInfo mTrackInfo;

    protected TrackMenuItem(JsonNode json, MenuElement element) {
        super(json, element, false);

        // temporary value
        mText1 = element.getText();
    }

    @Nonnull
    @Override
    public ImmutableList<IconRetriever> getIconRetrieverList() {
        return sIconRetrievers;
    }

    @Nonnull
    @Override
    protected ItemType calculateType() {
        return ItemType.IVT_THUMBTEXT2;
    }

    @Override
    @Nonnull
    synchronized public String getText1() {
        return mText1;
    }

    @Nonnull
    @Override
    synchronized public Optional<String> getText2() {
        return Optional.ofNullable(mText2);
    }

    @Nonnull
    @Override
    synchronized public Optional<String> getText3() {
        return Optional.ofNullable(mText3);
    }

    @Nonnull
    synchronized public Optional<String> getCoverId() {
        if (mTrackInfo == null) {
            return Optional.empty();
        }
        return mTrackInfo.getCoverId();
    }

    @Override
    public void prepare(ViewGroup parentView, ViewHolder holder, ThumbnailProcessor processor) {
        super.prepare(parentView, holder, processor);

        final TextView text1 = holder.text1;
        if (text1 == null) {
            // nothing we can do to prepare for this
            return;
        }

        // peek for track info in cache
        final String trackId = mMenuElement.getTrackId();
        if (trackId == null) {
            throw new IllegalStateException("track id is null for track menu item");
        }

        TrackInfo trackInfo = getTrackInfo();
        if (trackInfo == null) {
            trackInfo = TrackInfo.peek(SBContextProvider.get().getServerId(), trackId).orElse(null);
            if (trackInfo != null) {
                setTrackInfo(trackInfo);
            }
        }
        sLoadJobs.put(text1, this);

        if (trackInfo != null) {
            // track info is available, remove any pending jobs and just let normal binding occur
            processor.removeJob(text1);
        } else {
            // missing track info, trigger a load
            processor.addJob(text1, new Job() {
                @Override
                public boolean execute() {
                    try {
                        CacheFuture<TrackInfo> future = TrackInfo.load(SBContextProvider.get().getServerId(), trackId, MoreExecutors.newDirectExecutorService());
                        TrackInfo loadedTrackInfo = future.checkedGet(Constants.READ_TIMEOUT, Constants.TIME_UNITS);
                        setTrackInfo(loadedTrackInfo);
                        return true;
                    } catch (InterruptedException e) {
                        // ignore
                    } catch (Exception e) {
                        OSLog.w(e.getMessage(), e);
                    }
                    return false;
                }

                @Override
                public void commit() {
                    OSExecutors.getMainThreadExecutor().execute(() -> {
                        TrackMenuItem current = sLoadJobs.get(text1);
                        if (current != TrackMenuItem.this) {
                            // this job been superceded
                            return;
                        }

                        text1.setVisibility(View.VISIBLE);
                        text1.setText(getText1());

                        String text2 = getText2().orElse(null);
                        if (holder.text2 != null) {
                            holder.text2.setVisibility(text2 == null ? View.GONE : View.VISIBLE);
                            holder.text2.setText(Strings.nullToEmpty(text2));
                        }
                        String text3 = getText3().orElse(null);
                        if (holder.text3 != null) {
                            holder.text3.setVisibility(text3 == null ? View.GONE : View.VISIBLE);
                            holder.text3.setText(Strings.nullToEmpty(text3));
                        }

                        if (sTrackItemIconRetriever.applies(TrackMenuItem.this) && holder.icon != null) {
                            sTrackItemIconRetriever.load(processor, TrackMenuItem.this, (AbsListView) parentView, holder.icon);
                        }
                    });
                }

                @Override
                public void abort() {
                    // no implementation
                }
            });
        }
    }

    @Override
    public void preload(ThumbnailProcessor processor, AbsListView parent) throws InterruptedException {
        super.preload(processor, parent);

        final String trackId = mMenuElement.getTrackId();
        if (trackId == null) {
            throw new IllegalStateException("track id is null for track menu item");
        }

        // make this bounded?
        TrackInfo.load(SBContextProvider.get().getServerId(), trackId, OSExecutors.getUnboundedPool());
    }

    @Nullable
    synchronized TrackInfo getTrackInfo() {
        return mTrackInfo;
    }

    synchronized protected void setTrackInfo(TrackInfo trackInfo) {
        if (mTrackInfo == null) {
            mTrackInfo = trackInfo;
            mText1 = mTrackInfo.getText1();
            mText2 = mTrackInfo.getText2().orElse(null);
            mText3 = mTrackInfo.getText3().orElse(null);
        }
    }
}
