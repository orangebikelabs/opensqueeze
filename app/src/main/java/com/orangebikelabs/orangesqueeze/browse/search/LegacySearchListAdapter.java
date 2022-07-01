/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.ListJobProcessor.Job;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.menu.MenuListAdapter;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.util.Iterator;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class LegacySearchListAdapter extends MenuListAdapter {

    final private ConcurrentMap<String, AbsJob> mCompletedJobs = new MapMaker().makeMap();

    public LegacySearchListAdapter(Context context, ThumbnailProcessor processor) {
        super(context, processor);
    }

    @Override
    protected boolean bindActionButton(StandardMenuItem item, View actionButton) {
        return true;
    }

    @Override
    protected void bindStandardItem(ViewGroup parentView, View view, StandardMenuItem item, int pos) {
        // remove any jobs associated with this view
        mThumbnailProcessor.removeJob(view);

        super.bindStandardItem(parentView, view, item, pos);

        AbsJob job = getCompletionJob((LegacySearchItem) item);

        if (job != null) {
            String completionKey = job.getCompletionKey();
            AbsJob completedJob = mCompletedJobs.get(completionKey);
            if (completedJob != null) {
                completedJob.bindItems(view);
                if (completedJob.execute()) {
                    completedJob.commit();
                }
            } else {
                job.bindItems(view);
                mThumbnailProcessor.addJob(view, job);
            }
        }
    }

    @Override
    protected void onPreloadItem(AbsListView parent, Item item) throws InterruptedException {
        super.onPreloadItem(parent, item);
        if (item instanceof LegacySearchItem) {
            AbsJob job = getCompletionJob((LegacySearchItem) item);
            if (job != null && !mCompletedJobs.containsKey(job.getCompletionKey())) {
                mThumbnailProcessor.addSecondaryJob(job);
            }
        }
    }

    @Nullable
    protected AbsJob getCompletionJob(LegacySearchItem item) {
        AbsJob retval = null;

        if (item.getNode().has("album")) {
            JsonNode albumId = item.getNode().get("album_id");
            if (albumId != null) {
                retval = new FillAlbumInfoJob(albumId.asText());
            }
        } else if (item.getNode().has("contributor")) {
            JsonNode artistId = item.getNode().get("contributor_id");
            if (artistId != null) {
                retval = new FillArtistInfoJob(artistId.asText());
            }
        } else if (item.getNode().has("track")) {
            JsonNode trackId = item.getNode().get("track_id");
            if (trackId != null) {
                retval = new FillTrackInfoJob(trackId.asText());
            }
        }
        return retval;
    }

    static abstract class AbsJob implements Job {
        final protected String mItemId;
        final private String mCompletionKey;

        protected AbsJob(String itemId, String keyPrefix) {
            mCompletionKey = keyPrefix + ":" + itemId;
            mItemId = itemId;
        }

        // called on main thread
        abstract public void bindItems(View parentView);

        @Override
        public void abort() {
            // no abort needed
        }

        public String getCompletionKey() {
            return mCompletionKey;
        }
    }

    private class FillArtistInfoJob extends AbsJob {
        volatile protected ImageView mIcon;

        FillArtistInfoJob(String artistId) {
            super(artistId, "FillArtistInfo");
        }

        @Override
        public boolean execute() {
            // preload
            mThumbnailProcessor.addArtworkPreloadJob(mItemId, ArtworkType.ARTIST_THUMBNAIL);
            return true;
        }

        @Override
        public void bindItems(View parentView) {
            mIcon = parentView.findViewById(R.id.icon);
            if (mIcon != null) {
                mIcon.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void commit() {
            if (mIcon == null) {
                return;
            }

            OSExecutors.getMainThreadExecutor().submit(() -> mThumbnailProcessor.addArtworkJob(mIcon, mItemId, ArtworkType.ARTIST_THUMBNAIL, ScaleType.CENTER));
        }
    }

    private class FillAlbumInfoJob extends AbsJob {
        protected volatile TextView mTextView;
        protected volatile ImageView mIcon;
        protected String mArtworkId, mTextValue;

        public FillAlbumInfoJob(String itemId) {
            super(itemId, "FillAlbumInfo");
        }

        @Override
        public void bindItems(View parentView) {
            mTextView = parentView.findViewById(R.id.text2);
            if (mTextView != null) {
                mTextView.setText("");
            }
            mIcon = parentView.findViewById(R.id.icon);
            if (mIcon != null) {
                mIcon.setVisibility(View.VISIBLE);
                mThumbnailProcessor.setLoadingArtwork(mIcon);
            }
        }

        @Override
        public boolean execute() {
            boolean retval = false;
            SBRequest request = SBContextProvider.get().newRequest(SBRequest.Type.COMET, "albums", "0", "1", "album_id:" + mItemId, "tags:aj");
            FutureResult futureResult = request.submit(MoreExecutors.newDirectExecutorService());
            try {
                SBResult result = futureResult.checkedGet();
                for (ObjectNode node : JsonHelper.getObjects(result.getJsonResult().path("albums_loop"))) {
                    mTextValue = node.path("artist").asText();
                    mArtworkId = JsonHelper.getString(node, "artwork_track_id", null);
                    if (mArtworkId != null) {
                        mThumbnailProcessor.addArtworkPreloadJob(mArtworkId, ArtworkType.ALBUM_THUMBNAIL);
                    }
                }
                retval = true;
            } catch (SBRequestException e) {
                OSLog.w(e.getMessage(), e);
            } catch (InterruptedException e) {
                // ignore, request was interrupted
            }
            return retval;
        }

        @Override
        public void commit() {
            OSExecutors.getMainThreadExecutor().submit(() -> {
                if (mTextView != null && mTextValue != null) {
                    mTextView.setText(mTextValue);
                }
                if (mIcon != null && mArtworkId != null) {
                    mThumbnailProcessor.addArtworkJob(mIcon, mArtworkId, ArtworkType.ALBUM_THUMBNAIL, ScaleType.CENTER);
                }
            });
        }
    }

    private class FillTrackInfoJob extends FillAlbumInfoJob {
        public FillTrackInfoJob(String trackId) {
            super(trackId);
        }

        @Override
        public boolean execute() {
            boolean retval = false;
            SBRequest request = SBContextProvider.get().newRequest(SBRequest.Type.COMET, "songinfo", "0", "999999", "track_id:" + mItemId, "tags:Jac");
            FutureResult futureResult = request.submit(MoreExecutors.newDirectExecutorService());
            try {
                SBResult result = futureResult.checkedGet();
                JsonNode node = result.getJsonResult().get("songinfo_loop");
                if (node != null) {
                    JsonNode artworkIdNode = null;
                    for (int i = 0; i < node.size(); i++) {
                        JsonNode element = node.get(i);
                        Iterator<String> it = element.fieldNames();
                        while (it.hasNext()) {
                            String fieldName = it.next();
                            JsonNode value = element.get(fieldName);

                            if (fieldName.equals("artist")) {
                                mTextValue = value.asText();
                            } else if (fieldName.equals("artwork_track_id")) {
                                artworkIdNode = value;
                            } else if (fieldName.equals("coverid") && artworkIdNode == null) {
                                artworkIdNode = value;
                            }
                        }
                    }
                    if (artworkIdNode != null) {
                        mArtworkId = artworkIdNode.asText();
                        mThumbnailProcessor.addArtworkPreloadJob(mArtworkId, ArtworkType.ALBUM_THUMBNAIL);
                    }
                }
                retval = true;
            } catch (SBRequestException e) {
                OSLog.w(e.getMessage(), e);
            } catch (InterruptedException e) {
                // ignore, request was interrupted
            }
            return retval;
        }
    }
}
