/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.OSAssert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class ViewDownloadsAdapter extends BaseExpandableListAdapter {

    final private static int TYPICAL_FILE_SIZE = 2500000;

    @Nonnull
    final private ListMultimap<DownloadBatch, DownloadChild> mChildren = ArrayListMultimap.create();

    @Nonnull
    final private List<DownloadBatch> mBatches = new ArrayList<>();

    @Nonnull
    final private LinkedHashSet<DownloadBatch> mWorkingBatchList = new LinkedHashSet<>();

    /** map of batch names to batch objects, local to the adapter, persists across loader updates */
    @Nonnull
    private Map<String,DownloadBatch> mBatchCache = new HashMap<>();

    public ViewDownloadsAdapter(Context context) {
    }

    public void clear() {
        mChildren.clear();
        mBatches.clear();
        mBatchCache.clear();

        notifyDataSetChanged();
    }

    public void startUpdate() {
        // placeholder
        OSAssert.assertTrue(mWorkingBatchList.isEmpty(), "working batch list should be empty");
    }

    public void finalizeUpdate() {

        List<DownloadBatch> batchesToRemove = new ArrayList<>();
        for (DownloadBatch b : mChildren.keySet()) {
            if (!mWorkingBatchList.contains(b)) {
                batchesToRemove.add(b);
            }
        }

        for (DownloadBatch b : batchesToRemove) {
            mChildren.removeAll(b);
        }
        mBatches.clear();
        mBatches.addAll(mWorkingBatchList);

        mWorkingBatchList.clear();

        for (DownloadBatch batch : mChildren.keySet()) {
            int progressValue = 0, maxValue = 0, unstartedCount = 0, indeterminateSizeCount = 0, childCount = 0;

            for (DownloadChild child : mChildren.get(batch)) {
                childCount++;
                long current = child.getStatus().getBytesRead();
                Long length = child.getStatus().getContentLength().orNull();

                if (current == 0) {
                    unstartedCount++;
                } else {
                    progressValue += (int) current;
                }

                if (length == null) {
                    indeterminateSizeCount++;
                } else {
                    maxValue += length.intValue();
                }
            }

            if (indeterminateSizeCount == childCount) {
                // take a swag
                maxValue = TYPICAL_FILE_SIZE * childCount;
            } else if (indeterminateSizeCount > 0) {
                // use average max size of files to guess at future file sizes
                int averageMax = maxValue / (childCount - indeterminateSizeCount);
                maxValue += averageMax * indeterminateSizeCount;
            }

            batch.setProgressMax(maxValue);
            batch.setProgress(progressValue);
            batch.setProgressIndeterminate(false);
        }

        notifyDataSetChanged();
    }

    public void updateDownloadElement(String batchName, long downloadId, String title, DownloadStatus status) {
        DownloadBatch batch = mBatchCache.get(batchName);
        if(batch == null) {
            batch = new DownloadBatch(batchName);
            mBatchCache.put(batchName, batch);
        }

        List<DownloadChild> children = mChildren.get(batch);

        DownloadChild child = null;
        for (DownloadChild c : children) {
            if (c.getId() == downloadId) {
                child = c;

                c.setTitle(title);
                c.setStatus(status);
                break;
            }
        }
        mWorkingBatchList.add(batch);
        if (child == null) {
            child = new DownloadChild(downloadId, title, status);
            mChildren.put(batch, child);
        }

        long current = child.getStatus().getBytesRead();
        if (current == 0) {
            child.setProgressIndeterminate(status.isActive());

            child.setProgressMax(1);
            child.setProgress(0);
        } else {
            Long length = child.getStatus().getContentLength().orNull();
            if (length == null) {
                child.setProgressIndeterminate(status.isActive());
                child.setProgress(0);
                child.setProgressMax(1);
            } else {
                child.setProgressMax(length.intValue());
                child.setProgress((int) current);
                child.setProgressIndeterminate(false);
            }
        }
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        DownloadBatch batch = mBatches.get(groupPosition);
        return mChildren.get(batch).get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        DownloadChild child = (DownloadChild) getChild(groupPosition, childPosition);
        OSAssert.assertNotNull(child, "child should never be null");

        return child.getId();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        DownloadBatch batch = mBatches.get(groupPosition);
        return mChildren.get(batch).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return mBatches.get(groupPosition);
    }

    @Override
    public int getGroupCount() {
        return mBatches.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        DownloadBatch batch = (DownloadBatch) getGroup(groupPosition);
        OSAssert.assertNotNull(batch, "batch should never be null");

        return batch.getId();
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, @Nullable View convertView, ViewGroup parent) {
        View retval = convertView;
        if (retval == null) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            retval = inflater.inflate(R.layout.downloadslist_batchitem, parent, false);
            OSAssert.assertNotNull(retval, "item inflation should succeed");
        }

        DownloadBatch batch = (DownloadBatch) getGroup(groupPosition);
        OSAssert.assertNotNull(batch, "batch should not be null");

        TextView title = retval.findViewById(R.id.text1);
        OSAssert.assertNotNull(title, "item should have text item");

        title.setText(batch.getName());

        ProgressBar progress = retval.findViewById(R.id.progress);
        progress.setMax(batch.getProgressMax());
        progress.setProgress(batch.getProgress());
        progress.setIndeterminate(batch.isProgressIndeterminate());

        return retval;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, @Nullable View convertView, ViewGroup parent) {
        View retval = convertView;
        if (retval == null) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            retval = inflater.inflate(R.layout.downloadslist_downloaditem, parent, false);
            OSAssert.assertNotNull(retval, "item inflation should succeed");
        }

        DownloadChild child = (DownloadChild) getChild(groupPosition, childPosition);
        OSAssert.assertNotNull(child, "child should not be null");

        TextView title = retval.findViewById(R.id.text1);
        OSAssert.assertNotNull(title, "item should have text item");

        title.setText(child.getTitle());

        ProgressBar progress = retval.findViewById(R.id.progress);

        progress.setMax(child.getProgressMax());
        progress.setProgress(child.getProgress());
        progress.setIndeterminate(child.isProgressIndeterminate());

        return retval;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    static class DownloadChild {
        final private long mId;

        @Nonnull
        private String mTitle;

        @Nonnull
        private DownloadStatus mStatus;

        private boolean mProgressIndeterminate;

        private int mProgress;

        private int mProgressMax;

        public DownloadChild(long id, String title, DownloadStatus status) {
            mId = id;
            mTitle = title;
            mStatus = status;
        }

        public int getProgress() {
            return mProgress;
        }

        public void setProgress(int progress) {
            this.mProgress = progress;
        }

        public int getProgressMax() {
            return mProgressMax;
        }

        public void setProgressMax(int max) {
            this.mProgressMax = max;
        }

        public boolean isProgressIndeterminate() {
            return mProgressIndeterminate;
        }

        public void setProgressIndeterminate(boolean indeterminate) {
            mProgressIndeterminate = indeterminate;
        }

        public long getId() {
            return mId;
        }

        @Nonnull
        public String getTitle() {
            return mTitle;
        }

        @Nonnull
        public DownloadStatus getStatus() {
            return mStatus;
        }

        public void setTitle(String title) {
            mTitle = title;
        }

        public void setStatus(DownloadStatus status) {
            mStatus = status;
        }
    }

}