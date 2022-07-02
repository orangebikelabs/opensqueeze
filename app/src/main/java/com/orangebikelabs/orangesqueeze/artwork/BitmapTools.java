/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;

import com.google.common.io.ByteSink;
import com.google.common.math.IntMath;
import com.orangebikelabs.orangesqueeze.common.OSAssert;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class BitmapTools {

    final public static int MAX_BITMAP_OPERATIONS = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    /**
     * only allow the correct number of bitmap operations at a given time to keep memory usage reasonable
     */
    final private static Semaphore sBitmapOperationSemaphore = new Semaphore(MAX_BITMAP_OPERATIONS);

    static public void acquireBitmapOperation(boolean allowFromMainThread) {
        if (!allowFromMainThread) {
            OSAssert.assertNotMainThread();
        }
        sBitmapOperationSemaphore.acquireUninterruptibly();
    }

    static public void releaseBitmapOperation() {
        sBitmapOperationSemaphore.release();
    }

    static public int calculateSampleSize(int imageWidth, int imageHeight, int minDesiredWidth, int minDesiredHeight) {

        double widthScale = ((double) imageWidth) / minDesiredWidth;
        double heightScale = ((double) imageHeight) / minDesiredHeight;

        double bestScale = Math.min(widthScale, heightScale);

        int exponent = (int) (Math.floor(Math.log(bestScale) / Math.log(2.0d)));
        // no negative exponents
        exponent = Math.max(0, exponent);

        int retval = IntMath.pow(2, exponent);
        return retval;
    }

    /**
     * Perform bitmap scaling, try to use best quality scaling possible.
     */
    @Nullable
    static public Bitmap createScaledBitmap(Bitmap bmp, int desiredWidth, int desiredHeight) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        Config config = bmp.getConfig();

        if (width <= 0 || height <= 0 || config == null) {
            // if bmp width is odd, fallback
            return Bitmap.createScaledBitmap(bmp, desiredWidth, desiredHeight, true);
        } else {
            float desiredScale = Math.min((float) desiredWidth / bmp.getWidth(), (float) desiredHeight / bmp.getHeight());
            Matrix matrix = new Matrix();
            matrix.postScale(desiredScale, desiredScale);

            Bitmap newBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
            if (newBitmap.getHeight() != desiredHeight) {
                int diff = desiredHeight - newBitmap.getHeight();
                Bitmap finalTemp = Bitmap.createBitmap(desiredWidth, desiredHeight, config);
                Canvas c = new Canvas(finalTemp);
                c.drawBitmap(newBitmap, 0, diff / 2.0f, null);

                newBitmap.recycle();
                return finalTemp;
            } else if (newBitmap.getWidth() != desiredWidth) {
                int diff = desiredWidth - newBitmap.getWidth();
                Bitmap finalTemp = Bitmap.createBitmap(desiredWidth, desiredHeight, config);
                Canvas c = new Canvas(finalTemp);
                c.drawBitmap(newBitmap, diff / 2.0f, 0, null);
                newBitmap.recycle();
                return finalTemp;
            } else {
                return newBitmap;
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static int bitmapSize(Bitmap bmp) {
        Config config = bmp.getConfig();
        int byteCount = 2;
        if (config != null) {
            switch (config) {
                case ALPHA_8:
                    byteCount = 1;
                    break;
                case ARGB_8888:
                    byteCount = 4;
                    break;
                case RGBA_F16:
                    byteCount = 9;
                    break;
                case ARGB_4444:
                case RGB_565:

                // these last two are just fail-safes in case stuff gets added
                case HARDWARE:
                default:
                    byteCount = 2;
                    break;
            }
        }
        return byteCount * bmp.getHeight() * bmp.getWidth();
    }

    static public CompressFormat getStoredCompressFormat(ArtworkType type, int width) {
        CompressFormat retval = type.isThumbnail() ? CompressFormat.PNG : CompressFormat.JPEG;
        if (retval == CompressFormat.PNG && width > 150) {
            // if our thumbnails are large (grid, whatever) use JPG
            retval = CompressFormat.JPEG;
        }
        return retval;
    }

    static public int getStoredCompressQuality(CompressFormat format, int width) {
        if (format == CompressFormat.JPEG) {
            return 40;
        } else if (format == CompressFormat.PNG) {
            return 0;
        } else {
            throw new IllegalStateException();
        }
    }

    static public void compress(ArtworkType type, Bitmap bmp, ByteSink sink) throws IOException {
        sBitmapOperationSemaphore.acquireUninterruptibly();
        try (OutputStream os = sink.openStream()) {
            int width = bmp.getWidth();
            CompressFormat format = getStoredCompressFormat(type, width);
            int quality = getStoredCompressQuality(format, width);
            bmp.compress(format, quality, os);
        } finally {
            sBitmapOperationSemaphore.release();
        }
    }
}
