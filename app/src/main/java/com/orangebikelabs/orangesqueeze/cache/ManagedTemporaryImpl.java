/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.orangebikelabs.orangesqueeze.common.Closeables;
import com.orangebikelabs.orangesqueeze.common.FileUtils;
import com.orangebikelabs.orangesqueeze.common.OSLog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * @author tsandee
 */
@Keep
class ManagedTemporaryImpl implements ManagedTemporary {
    static final private int MEMORY_THRESHOLD = 64 * 1024;

    /**
     * removal listener that deletes the backing file for an expired or invalidated managed temporary
     */
    final static private RemovalListener<File, ManagedTemporary> sRemovalListener = removalNotification -> {
        File key = removalNotification.getKey();
        if (key != null && key.exists()) {
            if (OSLog.isLoggable(OSLog.VERBOSE)) {
                logVerboseMessage(key);
            }
            FileUtils.deleteChecked(key);
        }
    };

    /**
     * the definition for the backing managed temporary cache objects
     */
    final static protected Cache<File, ManagedTemporary> sManagedTemporaryCache = CacheBuilder.newBuilder().
            concurrencyLevel(2).
            removalListener(sRemovalListener).
            expireAfterWrite(60, TimeUnit.SECONDS).
            build();

    /**
     * to work around forward references, do this in separate method
     */
    static private void logVerboseMessage(File key) {
        OSLog.v("Deleting temporary managed file=" + key + ", size for managed temporary cache=" + sManagedTemporaryCache.size());
    }

    /**
     * creates a new managed temporary and starts managing it
     */
    @Nonnull
    static public ManagedTemporaryImpl newInstance(CacheConfiguration config) throws IOException {

        ManagedTemporaryImpl retval = new ManagedTemporaryImpl(config);

        sManagedTemporaryCache.put(retval.mFile, retval);

        return retval;

    }

    @Nonnull
    final protected File mFile;

    @Nonnull
    final protected Closeable mCloseTracker = Closeables.newCloseTracker();

    @Nullable
    volatile protected FileBackedOutputStream mFileBackedStream;

    private ManagedTemporaryImpl(CacheConfiguration configuration) throws IOException {
        mFile = File.createTempFile("managed_", ".tmp", configuration.getExpandedCacheDir());
        // don't use deleteOnExit because eventually we will run out of memory
    }

    @Override
    @Nonnull
    public ByteSink asByteSink() {
        return new ByteSink() {
            @Override
            public OutputStream openStream() {
                FileBackedOutputStream retval = new FileBackedOutputStream(mFile, MEMORY_THRESHOLD);
                mFileBackedStream = retval;
                return retval;
            }
        };
    }

    @Override
    @Nonnull
    public ByteSource asByteSource() {
        FileBackedOutputStream fbos = mFileBackedStream;
        if (fbos != null) {
            return fbos.asByteSource();
        } else {
            return ByteSource.wrap(new byte[0]);
        }
    }

    @Override
    public boolean isInMemory() {
        FileBackedOutputStream fbos = mFileBackedStream;
        if (fbos != null) {
            return fbos.inMemory();
        } else {
            return true;
        }
    }

    @Override
    public long size() {
        FileBackedOutputStream fileBackedOutputStream = mFileBackedStream;
        if (fileBackedOutputStream != null) {
            return fileBackedOutputStream.length();
        } else {
            return 0;
        }
    }

    @Override
    public void close() throws IOException {
        sManagedTemporaryCache.invalidate(mFile);
        mCloseTracker.close();
    }

    @Override
    @Nonnull
    public String toString() {
        FileBackedOutputStream fbos = mFileBackedStream;
        boolean inMemory = (fbos == null || fbos.inMemory());

        return MoreObjects.toStringHelper(this).add("file", mFile).add("size", size()).add("inMemory", inMemory).toString();
    }

	/*
    * Copyright (C) 2008 The Guava Authors
	*
	* Licensed under the Apache License, Version 2.0 (the "License");
	* you may not use this file except in compliance with the License.
	* You may obtain a copy of the License at
	*
	* http://www.apache.org/licenses/LICENSE-2.0
	*
	* Unless required by applicable law or agreed to in writing, software
	* distributed under the License is distributed on an "AS IS" BASIS,
	* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	* See the License for the specific language governing permissions and
	* limitations under the License.
	*/

    /**
     * An {@link OutputStream} that starts buffering to a byte array, but switches to file buffering once the data reaches a configurable
     * size.
     * <p/>
     * <p/>
     * This class is thread-safe.
     *
     * @author Chris Nokleberg
     * @since 1.0
     */
    static private class FileBackedOutputStream extends OutputStream {

        private final int fileThreshold;

        final private File mTargetFile;

        @GuardedBy("this")
        private OutputStream out;

        @GuardedBy("this")
        private CustomByteArrayOutputStream memory;

        @GuardedBy("this")
        private File file;

        /**
         * Creates a new instance that uses the given file threshold, and does not reset the data when the {@link ByteSource} returned by
         * {@link #asByteSource()} asByteSource} is finalized.
         *
         * @param fileThreshold the number of bytes before the stream should switch to buffering to a file
         */
        public FileBackedOutputStream(File targetFile, int fileThreshold) {
            this.fileThreshold = fileThreshold;
            mTargetFile = targetFile;
            memory = new CustomByteArrayOutputStream(16 * 1024);
            out = memory;
        }

        @Nonnull
        public synchronized ByteSource asByteSource() {
            if (file != null) {
                return Files.asByteSource(file);
            } else {
                return new ByteSource() {
                    @Override
                    public InputStream openStream() throws IOException {
                        return FileBackedOutputStream.this.openStream();
                    }
                };
            }
        }

        @Nonnull
        protected synchronized InputStream openStream() throws IOException {
            if (file != null) {
                return new FileInputStream(file);
            } else {
                return memory.openInputStream();
            }
        }

        @Override
        public synchronized void write(int b) throws IOException {
            update(1);
            out.write(b);
        }

        @Override
        public synchronized void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            update(len);
            out.write(b, off, len);
        }

        @Override
        public synchronized void close() throws IOException {
            out.close();
        }

        @Override
        public synchronized void flush() throws IOException {
            out.flush();
        }

        synchronized public int length() {
            if (file == null) {
                return memory.getCount();
            } else {
                return (int) file.length();
            }
        }

        synchronized public boolean inMemory() {
            return file == null;
        }

        /**
         * Checks if writing {@code len} bytes would go over threshold, and switches to file buffering if so.
         */
        private void update(int len) throws IOException {
            if (file == null && (memory.getCount() + len > fileThreshold)) {
                FileOutputStream transfer = new FileOutputStream(mTargetFile);
                transfer.write(memory.getBuffer(), 0, memory.getCount());
                transfer.flush();

                // We've successfully transferred the data; switch to writing to file
                out = transfer;
                file = mTargetFile;
                memory = null;
            }
        }
    }

	/*
     * Copyright (C) 2012 The Android Open Source Project
	 *
	 * Licensed under the Apache License, Version 2.0 (the "License");
	 * you may not use this file except in compliance with the License.
	 * You may obtain a copy of the License at
	 *
	 *      http://www.apache.org/licenses/LICENSE-2.0
	 *
	 * Unless required by applicable law or agreed to in writing, software
	 * distributed under the License is distributed on an "AS IS" BASIS,
	 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	 * See the License for the specific language governing permissions and
	 * limitations under the License.
	 */

    /**
     * A variation of {@link java.io.ByteArrayOutputStream} that uses a pool of byte[] buffers instead of always allocating them fresh,
     * saving on heap churn.
     */
    static private class CustomByteArrayOutputStream extends ByteArrayOutputStream {
        byte[] getBuffer() {
            return buf;
        }

        int getCount() {
            return count;
        }

        @Nonnull
        InputStream openInputStream() {
            return new ByteArrayInputStream(buf, 0, count);
        }

        /**
         * Constructs a new {@code ByteArrayOutputStream} with a default size of {@code size} bytes. If more than {@code size} bytes are
         * written to this instance, the underlying byte array will expand.
         *
         * @param size initial size for the underlying byte array.
         */
        public CustomByteArrayOutputStream(int size) {
            buf = new byte[size];
        }

        /**
         * Ensures there is enough space in the buffer for the given number of additional bytes.
         */
        private void expand(int i) {
            /* Can the buffer handle @i more bytes, if not expand it */
            if (count + i <= buf.length) {
                return;
            }
            byte[] newbuf = new byte[(count + i) * 2];
            System.arraycopy(buf, 0, newbuf, 0, count);
            buf = newbuf;
        }

        @Override
        public synchronized void write(byte[] buffer, int offset, int len) {
            expand(len);
            super.write(buffer, offset, len);
        }

        @Override
        public synchronized void write(int oneByte) {
            expand(1);
            super.write(oneByte);
        }
    }
}
