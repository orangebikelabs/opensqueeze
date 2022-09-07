/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.os.SystemClock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import arrow.core.Option;

/**
 * Class that represents the current serialized download status.
 *
 * @author tsandee
 */
@ThreadSafe
public class DownloadStatus {
    static final public int STATUS_DOWNLOAD_NOT_STARTED = 0;
    static final public int STATUS_DOWNLOAD_FAILED = 1;
    static final public int STATUS_DOWNLOAD_FAILED_RETRY = 2;
    static final public int STATUS_DOWNLOAD_SUCCESS = 3;
    static final public int STATUS_DOWNLOAD_ACTIVE = 4;

    static final public int DOWNLOAD_VERSION = 1;

    static final private String FIELD_VERSION = "version";
    static final private String FIELD_STATUS = "status";
    static final private String FIELD_FAILUREREASON = "failureReason";
    static final private String FIELD_CONTENTLENGTH = "contentLength";
    static final private String FIELD_BYTESREAD = "bytesRead";
    static final private String FIELD_SUCCESSTIMESTAMP = "successTimestamp";

    @Nonnull
    public static DownloadStatus fromJson(@Nullable String json) {
        DownloadStatus retval = new DownloadStatus();
        if (!Strings.isNullOrEmpty(json)) {
            try {
                JsonNode node = JsonHelper.getJsonObjectMapper().readTree(json);
                int version = node.path(FIELD_VERSION).asInt();
                if (version == DOWNLOAD_VERSION) {
                    retval.setStatus(node.path(FIELD_STATUS).asInt());
                    if (node.has(FIELD_FAILUREREASON)) {
                        retval.setFailureReason(node.get(FIELD_FAILUREREASON).asText());
                    }
                    if (node.has(FIELD_CONTENTLENGTH)) {
                        retval.setContentLength(node.path(FIELD_CONTENTLENGTH).asLong());
                    }
                    retval.setBytesRead(node.path(FIELD_BYTESREAD).asLong());
                    retval.setSuccessTimestamp(node.path(FIELD_SUCCESSTIMESTAMP).asLong());
                } else if (version == 0) {
                    // load old version of the download spec
                    if (node.path("success").asBoolean()) {
                        retval.markSuccess(node.path("bytesRead").asLong());
                    } else {
                        retval.markFailed(node.path("failureReason").asText(), false);
                    }
                }
            } catch (IOException e) {
                Reporting.report(e);
            }
        }
        return retval;
    }

    @GuardedBy("this")
    private int mStatus = STATUS_DOWNLOAD_NOT_STARTED;

    @GuardedBy("this")
    @Nullable
    private String mFailureReason;

    @GuardedBy("this")
    @Nullable
    private Long mContentLength;

    @GuardedBy("this")
    private long mBytesRead;

    @GuardedBy("this")
    private long mSuccessTimestamp;

    public DownloadStatus() {
    }

    synchronized public boolean isActive() {
        return mStatus == STATUS_DOWNLOAD_ACTIVE;
    }

    @Nonnull
    synchronized public String toJson() {
        ObjectNode node = JsonHelper.getJsonObjectMapper().createObjectNode();
        node.put(FIELD_VERSION, DOWNLOAD_VERSION);
        node.put(FIELD_STATUS, mStatus);
        if (mFailureReason != null) {
            node.put(FIELD_FAILUREREASON, mFailureReason);
        }
        if (mContentLength != null) {
            node.put(FIELD_CONTENTLENGTH, mContentLength);
        }
        node.put(FIELD_BYTESREAD, mBytesRead);
        node.put(FIELD_SUCCESSTIMESTAMP, mSuccessTimestamp);
        return node.toString();
    }

    @Nonnull
    public synchronized Option<Long> getContentLength() {
        return Option.fromNullable(mContentLength);
    }

    public synchronized void setContentLength(long contentLength) {
        mContentLength = contentLength;
    }

    public synchronized long getBytesRead() {
        return mBytesRead;
    }

    public synchronized void setBytesRead(long bytesRead) {
        mBytesRead = bytesRead;
    }

    public synchronized void markActive() {
        mStatus = STATUS_DOWNLOAD_ACTIVE;
    }

    public synchronized void markFailed(String reason, boolean retry) {
        mStatus = retry ? STATUS_DOWNLOAD_FAILED_RETRY : STATUS_DOWNLOAD_FAILED;
        mFailureReason = reason;
    }

    public synchronized void markSuccess(long length) {
        mStatus = STATUS_DOWNLOAD_SUCCESS;
        mContentLength = length;
        mBytesRead = length;
        mFailureReason = null;
        mSuccessTimestamp = System.currentTimeMillis();
    }

    protected synchronized void setStatus(int status) {
        mStatus = status;
    }

    protected synchronized void setSuccessTimestamp(long timestamp) {
        mSuccessTimestamp = timestamp;
    }

    protected synchronized void setFailureReason(@Nullable String reason) {
        mFailureReason = reason;
    }

    public void update(long id) {
        OSAssert.assertNotMainThread();

        synchronized (this) {
            Long completionTimestamp = null;
            if (mStatus == STATUS_DOWNLOAD_SUCCESS) {
                completionTimestamp = mSuccessTimestamp;
            }

            // autostart again if the service comes back
            boolean autoStart;
            switch (mStatus) {
                case STATUS_DOWNLOAD_FAILED_RETRY:
                case STATUS_DOWNLOAD_NOT_STARTED:
                case STATUS_DOWNLOAD_ACTIVE:
                    autoStart = true;
                    break;
                default:
                    autoStart = false;
                    break;
            }

            DatabaseAccess.getInstance(SBContextProvider.get().getApplicationContext())
                    .getDownloadQueries()
                    .updateStatus(completionTimestamp, this, autoStart, id);
        }
    }

    public InputStream newTrackingInputStream(long id, InputStream wrapper) {
        return new StatusInputStream(id, wrapper);
    }

    static final private int TIME_THRESHOLD = 2000; //ms

    private class StatusInputStream extends FilterInputStream {

        private long mCount;
        private long mNextUpdateTimestamp;

        final private long mId;

        /**
         * Wraps another input stream, counting the number of bytes read.
         *
         * @param in the input stream to be wrapped
         */
        protected StatusInputStream(long id, InputStream in) {
            super(in);

            mId = id;
        }

        @Override
        public int read() throws IOException {
            int result = in.read();
            if (result != -1) {
                mCount++;
            }
            checkUpdate();
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = in.read(b, off, len);
            if (result != -1) {
                mCount += result;
            }
            checkUpdate();
            return result;
        }

        @Override
        public void close() throws IOException {
            update(mId);

            super.close();
        }

        private void checkUpdate() {
            long current = SystemClock.uptimeMillis();
            if (current > mNextUpdateTimestamp) {
                setBytesRead(mCount);

                mNextUpdateTimestamp = current + TIME_THRESHOLD;

                OSLog.i("updating download status bytes read=" + mCount + ", total length=" + getContentLength());
                update(mId);
            }
        }
    }
}