/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import javax.annotation.Nonnull;

import okio.Buffer;
import okio.Okio;
import okio.Sink;
import okio.Source;
import okio.Timeout;

/**
 * More Okio support classes.
 */
public class MoreOkio {
    @Nonnull
    static public SourceSupplier supplier(File file) {
        return new FileBufferedSourceSupplier(file);
    }

    @Nonnull
    static public SourceSupplier supplier(byte[] bytes) {
        return new FixedMemoryBufferedSourceSupplier(bytes);
    }

    @Nonnull
    static public CountingSource counting(Source source) {
        return new CountingSourceImpl(source);
    }


    @Nonnull
    static public CountingSink counting(Sink sink) {
        return new CountingSinkImpl(sink);
    }

    @Nonnull
    static public Source source(byte[] bytes) {
        return Okio.source(new ByteArrayInputStream(bytes));
    }

    static private class FileBufferedSourceSupplier implements SourceSupplier {
        final private File mFile;

        FileBufferedSourceSupplier(File file) {
            mFile = file;
        }

        @Nonnull
        @Override
        public Source getSource() throws IOException {
            return Okio.source(mFile);
        }
    }

    static private class FixedMemoryBufferedSourceSupplier implements SourceSupplier {
        final private byte[] mBytes;

        FixedMemoryBufferedSourceSupplier(byte[] bytes) {
            mBytes = bytes;
        }

        @Nonnull
        @Override
        public Source getSource() {
            return MoreOkio.source(mBytes);
        }
    }

    static private class CountingSourceImpl implements CountingSource {
        final private Source mDelegate;
        private long mReadCount;

        protected CountingSourceImpl(Source delegate) {
            mDelegate = delegate;
        }

        @Override
        synchronized public long getReadCount() {
            return mReadCount;
        }

        @Override
        synchronized public void resetReadCount() {
            mReadCount = 0;
        }

        @Override
        public long read(Buffer buffer, long l) throws IOException {
            long retval = mDelegate.read(buffer, l);
            if (retval > 0) {
                synchronized(this) {
                    mReadCount += retval;
                }
            }
            return retval;
        }

        @Override
        @Nonnull
        public Timeout timeout() {
            return mDelegate.timeout();
        }

        @Override
        public void close() throws IOException {
            mDelegate.close();
        }
    }

    static private class CountingSinkImpl implements CountingSink {
        final private Sink mDelegate;
        private long mWriteCount;

        protected CountingSinkImpl(Sink delegate) {
            mDelegate = delegate;
        }

        @Override
        synchronized public long getWriteCount() {
            return mWriteCount;
        }

        @Override
        synchronized public void resetWriteCount() {
            mWriteCount = 0;
        }

        @Override
        public void write(Buffer buffer, long l) throws IOException {
            mDelegate.write(buffer, l);
            if (l > 0) {
                synchronized(this) {
                    mWriteCount += l;
                }
            }
        }

        @Override
        public void flush() throws IOException {
            mDelegate.flush();
        }

        @Override
        @Nonnull
        public Timeout timeout() {
            return mDelegate.timeout();
        }

        @Override
        public void close() throws IOException {
            mDelegate.close();
        }
    }

}