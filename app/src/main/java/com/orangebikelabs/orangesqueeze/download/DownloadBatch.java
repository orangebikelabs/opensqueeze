/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;

/**
 * Represents a download batch in the download adapter.
 */
class DownloadBatch {
    final private static AtomicLong sIdGenerator = new AtomicLong(0);

    @Nonnull
    final private String mName;

    final private long mId;

    private boolean mProgressIndeterminate;

    private int mProgress;

    private int mProgressMax;

    DownloadBatch(String name) {
        mName = name;
        mId = sIdGenerator.incrementAndGet();
    }

    public long getId() {
        return mId;
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

    @Nonnull
    public String getName() {
        return mName;
    }

    /**
     * use identity equals
     */
    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * use identity hashcode
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
