/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;

import com.google.common.io.ByteSource;
import com.google.common.io.Closer;
import com.orangebikelabs.orangesqueeze.artwork.BitmapRecycler.Criteria;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.Reporting;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class BitmapDecoder {

    @Nonnull
    public static BitmapDecoder getInstance(Context context) {
        return new BitmapDecoder(context, false);
    }

    @Nonnull
    public static BitmapDecoder getInstance(Context context, ArtworkType type) {
        return new BitmapDecoder(context, type.isThumbnail());
    }

    final private boolean mRecycling;

    @Nonnull
    final private DisplayMetrics mDisplayMetrics;

    @Nonnull
    final private BitmapRecycler mRecycler;

    private boolean mAllowFromMainThread;

    private BitmapDecoder(Context context, boolean useRecycling) {
        mRecycling = useRecycling;
        mDisplayMetrics = context.getResources().getDisplayMetrics();

        Context applicationContext = context.getApplicationContext();
        OSAssert.assertNotNull(applicationContext, "application context shouldn't be null");

        mRecycler = BitmapRecycler.getInstance(applicationContext);
    }

    public void setAllowFromMainThread(boolean allowFromMainThread) {
        mAllowFromMainThread = allowFromMainThread;
    }

    @Nonnull
    public Bitmap decode(ByteSource src) throws IOException {
        BitmapTools.acquireBitmapOperation(mAllowFromMainThread);
        try (InputStream is = src.openStream()) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            checkBitmapReturn(bmp);
            return bmp;
        } finally {
            BitmapTools.releaseBitmapOperation();
        }
    }

    private void checkBitmapReturn(@Nullable Bitmap bmp) throws IOException {
        if (bmp == null) {
            throw new IOException("unable to decode bitmap");
        }
    }

    @Nonnull
    public Bitmap decode(byte[] bytes) throws IOException {
        BitmapTools.acquireBitmapOperation(mAllowFromMainThread);
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            checkBitmapReturn(bmp);
            return bmp;
        } finally {
            BitmapTools.releaseBitmapOperation();
        }
    }

    final private static int MAX_PASS = 5;

    @Nonnull
    public RecyclableBitmap decodeScaledBitmapInexact(ByteSource byteSource, Bitmap.Config config, int startingWidth, int startingHeight) throws IOException {
        BitmapFactory.Options bounds = decodeHeader(byteSource);

        int sampleSize = BitmapTools.calculateSampleSize(bounds.outWidth, bounds.outHeight, startingWidth, startingHeight);
        for (int i = 0; i < MAX_PASS; i++) {
            try {
                return internalDecodeBitmap(byteSource, bounds, sampleSize, config);
            } catch (OutOfMemoryError e) {
                // failed to decode image at initial sample size
                sampleSize *= 2;
            }
        }
        String message = "Unable to decode image with bounds " + bounds.outWidth + "," + bounds.outHeight + " due to OOM situation";
        Reporting.report(message);
        throw new IOException(message);
    }

    @Nonnull
    public RecyclableBitmap decodeScaledBitmapInexact(byte[] bytes, Bitmap.Config config, int startingWidth, int startingHeight) throws IOException {
        BitmapFactory.Options bounds = decodeHeader(bytes);

        int sampleSize = BitmapTools.calculateSampleSize(bounds.outWidth, bounds.outHeight, startingWidth, startingHeight);
        for (int i = 0; i < MAX_PASS; i++) {
            try {
                return internalDecodeBitmap(bytes, bounds, sampleSize, config);
            } catch (OutOfMemoryError e) {
                // failed to decode image at initial sample size
                sampleSize *= 2;
            }
        }
        String message = "Unable to decode image with bounds " + bounds.outWidth + "," + bounds.outHeight + " due to OOM situation";
        Reporting.report(message);
        throw new IOException(message);
    }

    /**
     * Read the image from the stream and create a bitmap scaled to the desired size. Resulting bitmap will be roughly no larger than twice
     * the device screen size.
     */
    @Nonnull
    public RecyclableBitmap decodeScaledBitmapForDeviceDisplay(ByteSource byteSource, Bitmap.Config config) throws IOException {
        final int minimumStartingSize = Math.min(mDisplayMetrics.heightPixels, mDisplayMetrics.widthPixels);
        return decodeScaledBitmapInexact(byteSource, config, minimumStartingSize, minimumStartingSize);
    }

    /**
     * Read the image from the stream and create a bitmap scaled to the desired size. Resulting bitmap will be roughly no larger than twice
     * the device screen size.
     */
    @Nonnull
    public RecyclableBitmap decodeScaledBitmapForDeviceDisplay(byte[] bytes, Bitmap.Config config) throws IOException {
        final int minimumStartingSize = Math.min(mDisplayMetrics.heightPixels, mDisplayMetrics.widthPixels);
        return decodeScaledBitmapInexact(bytes, config, minimumStartingSize, minimumStartingSize);
    }

    /**
     * Decode the header of the image.
     */
    @Nonnull
    public BitmapFactory.Options decodeHeader(ByteSource byteSource) throws IOException {

        BitmapFactoryOptionsManager retval;


        Closer closer = Closer.create();
        try {
            retval = closer.register(newBitmapFactoryOptions(mDisplayMetrics, Config.ARGB_8888));
            retval.get().inJustDecodeBounds = true;

            InputStream is = closer.register(getInputStream(byteSource));
            BitmapFactory.decodeStream(is, null, retval.get());

            return retval.get();
        } catch (Throwable e) {
            throw closer.rethrow(e);
        } finally {
            closer.close();
        }
    }

    /**
     * Decode the header of the image.
     */
    @Nonnull
    public BitmapFactory.Options decodeHeader(byte[] bytes) {
        BitmapFactoryOptionsManager retval = newBitmapFactoryOptions(mDisplayMetrics, Config.ARGB_8888);
        try {
            retval.get().inJustDecodeBounds = true;

            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, retval.get());
            return retval.get();
        } finally {
            retval.close();
        }
    }

    /**
     * Read the image from the stream and create a bitmap scaled to the desired size. Resulting bitmap will be at least as large as the
     * desired minimum specified dimensions and will keep the image proportions correct during scaling.
     */
    @Nonnull
    private RecyclableBitmap internalDecodeBitmap(ByteSource byteSource, BitmapFactory.Options bounds, int sampleSize, Bitmap.Config bitmapConfiguration) throws IOException {
        Criteria recycleCriteria = null;
        if (mRecycling && sampleSize == 1) {
            recycleCriteria = BitmapRecycler.newCriteria(bounds.outWidth, bounds.outHeight, bounds.outMimeType);
        }

        BitmapTools.acquireBitmapOperation(mAllowFromMainThread);
        try {
            return internalDecodeBitmap2(byteSource, sampleSize, bitmapConfiguration, recycleCriteria);
        } finally {
            BitmapTools.releaseBitmapOperation();
        }
    }

    /**
     * Read the image from the stream and create a bitmap scaled to the desired size. Resulting bitmap will be at least as large as the
     * desired minimum specified dimensions and will keep the image proportions correct during scaling.
     */
    @Nonnull
    private RecyclableBitmap internalDecodeBitmap(byte[] bytes, BitmapFactory.Options bounds, int sampleSize, Bitmap.Config bitmapConfiguration) throws IOException {
        Criteria recycleCriteria = null;
        if (mRecycling && sampleSize == 1) {
            recycleCriteria = BitmapRecycler.newCriteria(bounds.outWidth, bounds.outHeight, bounds.outMimeType);
        }

        BitmapTools.acquireBitmapOperation(mAllowFromMainThread);
        try {
            return internalDecodeBitmap2(bytes, sampleSize, bitmapConfiguration, recycleCriteria);
        } finally {
            BitmapTools.releaseBitmapOperation();
        }
    }

    @Nonnull
    private RecyclableBitmap internalDecodeBitmap2(ByteSource byteSource, int sampleSize, Bitmap.Config bitmapConfiguration, @Nullable Criteria recycleCriteria) throws IOException {
        Closer closer = Closer.create();
        try {
            BitmapFactoryOptionsManager options = closer.register(newBitmapFactoryOptions(mDisplayMetrics, bitmapConfiguration));
            options.get().inSampleSize = sampleSize;

            boolean setPreferredConfig = true;

            RecyclableBitmap retval = null;
            if (recycleCriteria != null) {
                retval = mRecycler.get(recycleCriteria);
                if (retval != null) {
                    setPreferredConfig = false;
                    options.get().inBitmap = retval.get();
                }
            }

            if (setPreferredConfig) {
                options.get().inPreferredConfig = bitmapConfiguration;
            }

            InputStream is = closer.register(getInputStream(byteSource));

            Bitmap readyBitmap = BitmapFactory.decodeStream(is, null, options.get());
            checkBitmapReturn(readyBitmap);

            if (retval != null) {
                if (readyBitmap != retval.get()) {
                    // recycling didn't work, put this back in the pool immediately
                    OSLog.v(Tag.ARTWORK, "failed to use recycled bitmap");
                    retval.recycle();
                    retval = null;
                } else {
                    OSLog.v(Tag.ARTWORK, "reused recycled bitmap");
                }
            }
            // now, if retval is null...
            if (retval == null) {
                if (recycleCriteria != null) {
                    // add this new bitmap to the pool when it is recycled
                    retval = mRecycler.newRecyclableBitmap(readyBitmap);
                } else {
                    // don't make this bitmap poolable
                    retval = RecyclableBitmap.newSimpleRecyclableInstance(readyBitmap);
                }
            }
            return retval;
        } catch (Throwable t) {
            throw closer.rethrow(t);
        } finally {
            closer.close();
        }
    }

    @Nonnull
    private RecyclableBitmap internalDecodeBitmap2(byte[] bytes, int sampleSize, Bitmap.Config bitmapConfiguration, @Nullable Criteria recycleCriteria) throws IOException {
        BitmapFactoryOptionsManager options = newBitmapFactoryOptions(mDisplayMetrics, bitmapConfiguration);
        try {
            options.get().inSampleSize = sampleSize;

            boolean setPreferredConfig = true;

            RecyclableBitmap retval = null;
            if (recycleCriteria != null) {
                retval = mRecycler.get(recycleCriteria);
                if (retval != null) {
                    setPreferredConfig = false;
                    options.get().inBitmap = retval.get();
                }
            }

            if (setPreferredConfig) {
                options.get().inPreferredConfig = bitmapConfiguration;
            }

            Bitmap readyBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options.get());
            checkBitmapReturn(readyBitmap);

            if (retval != null) {
                if (readyBitmap != retval.get()) {
                    // recycling didn't work, put this back in the pool immediately
                    OSLog.v(Tag.ARTWORK, "failed to use recycled bitmap");
                    retval.recycle();
                    retval = null;
                } else {
                    OSLog.v(Tag.ARTWORK, "reused recycled bitmap");
                }
            }
            // now, if retval is null...
            if (retval == null) {
                if (recycleCriteria != null) {
                    // add this new bitmap to the pool when it is recycled
                    retval = mRecycler.newRecyclableBitmap(readyBitmap);
                } else {
                    // don't make this bitmap poolable
                    retval = RecyclableBitmap.newSimpleRecyclableInstance(readyBitmap);
                }
            }
            return retval;
        } finally {
            options.close();
        }
    }

    @Nonnull
    private BitmapFactoryOptionsManager newBitmapFactoryOptions(DisplayMetrics dm, Bitmap.Config config) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        // we do our own scaling
        options.inScaled = false;
        options.inDensity = dm.densityDpi;

        if (mRecycling) {
            // make sure a mutable bitmap is returned if we're recycling them
            options.inMutable = true;
        }
        options.inPreferredConfig = config;
        return new BitmapFactoryOptionsManager(options);
    }

    @Nonnull
    private InputStream getInputStream(ByteSource byteSource) throws IOException {
        InputStream retval = byteSource.openStream();
        if (!retval.markSupported()) {
            // use our own bufferedinputstream that uses the same buffer for all of our decodes rather than the android defaults that allocate 8K every time
            retval = new BufferedInputStream(retval, 4 * 1024);
        }
        return retval;
    }

    final static private Object[] sBuffers = new Object[BitmapTools.MAX_BITMAP_OPERATIONS];

    @Nonnull
    static private byte[] getBuffer() {
        byte[] retval = null;
        synchronized (sBuffers) {
            for (int i = 0; i < sBuffers.length; i++) {
                if (sBuffers[i] != null) {
                    retval = (byte[]) sBuffers[i];
                    sBuffers[i] = null;
                    break;
                }
            }
        }
        if (retval == null) {
            retval = new byte[16 * 1024];
        }
        return retval;
    }

    static private void releaseBuffer(byte[] buffer) {
        synchronized (sBuffers) {
            for (int i = 0; i < sBuffers.length; i++) {
                if (sBuffers[i] == null) {
                    sBuffers[i] = buffer;
                    break;
                }
            }
        }
    }

    /**
     * helper that implements closeable and allocates the temporary byte buffer out of the byte array pool provider
     */
    static private class BitmapFactoryOptionsManager implements Closeable {
        @Nonnull
        final private BitmapFactory.Options mOptions;

        BitmapFactoryOptionsManager(BitmapFactory.Options options) {
            mOptions = options;
            mOptions.inTempStorage = getBuffer();
        }

        @Nonnull
        BitmapFactory.Options get() {
            return mOptions;
        }

        @Override
        public void close() {
            byte[] buffer = mOptions.inTempStorage;
            mOptions.inTempStorage = null;

            if (buffer != null) {
                releaseBuffer(buffer);
            }
        }

    }
}
