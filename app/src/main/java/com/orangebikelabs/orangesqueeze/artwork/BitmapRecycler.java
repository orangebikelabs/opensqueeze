/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.util.concurrent.Atomics;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.common.OSAssert;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;

/**
 * Back of the hand calculations for bitmap sizes:
 * <p/>
 * <p/>
 * It's based on memory usage, appropriate memory usage is about the amount of memory used to display a full-screen bitmap. We really want
 * our memory tied up in decoded bitmaps above and below the current location.
 *
 * @author tsandee
 */
abstract public class BitmapRecycler {

    final static AtomicReference<BitmapRecycler> sRecyclerInstance = Atomics.newReference();

    @Nonnull
    static public BitmapRecycler getInstance(Context context) {
        BitmapRecycler retval = sRecyclerInstance.get();
        if (retval == null) {
            Context appContext = context.getApplicationContext();
            OSAssert.assertNotNull(appContext, "application context shouldn't be null");
            sRecyclerInstance.compareAndSet(null, new DefaultBitmapRecycler(appContext));
            retval = sRecyclerInstance.get();
        }
        return retval;
    }

    /**
     * marker interface for opaque bitmap criteria
     */
    public interface Criteria {
        // intentionally blank
    }

    @Nullable
    static public Criteria newCriteria(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        return newCriteria(width, height, "image/png");
    }

    @Nullable
    static public Criteria newCriteria(int width, int height, @Nullable String mimeType) {
        if (width == 0 || height == 0 || mimeType == null) {
            return null;
        }

        // only cache square images, others are strange and uncommon enough to avoid using the bitmap recycling engine
        if (width != height) {
            return null;
        }

        // only recycle jpeg and png images
        if (!mimeType.equals("image/jpeg") && !mimeType.equals("image/png")) {
            return null;
        }

        return new CriteriaImpl(width, height);
    }

    abstract public String memoryMetrics();

    abstract public void clear();

    abstract public void add(Bitmap bmp);

    @Nullable
    abstract public RecyclableBitmap get(Criteria criteria);

    @Nonnull
    abstract public RecyclableBitmap newRecyclableBitmap(Bitmap bmp);

    /**
     * default bitmap recycler for Honeycomb and higher
     */
    private static class DefaultBitmapRecycler extends BitmapRecycler {

        @GuardedBy("this")
        final private ListMultimap<Criteria, Bitmap> mRecycledBitmaps = LinkedListMultimap.create();

        final private int mMaximumSize;

        /**
         * the number of bytes of bitmaps currently in memory
         */
        @GuardedBy("this")
        private int mCurrentSize;

        /**
         * the last criteria object requested, which is a good enough mechanism to detect what types of criteria we should flush if we need
         * more room
         */
        @Nullable
        @GuardedBy("this")
        private Criteria mLastCriteria;

        protected DefaultBitmapRecycler(Context context) {
            OSAssert.assertApplicationContext(context);

			/*
             * Initialize cache sizing. Examples:
			 * Nexus 10 = 2560 x 1600 x 3 = ~6mb (really!)
			 * Nexus 7 = 1280 x 800 x 3 = 1.5mb
			 * Galaxy Nexus = 1280 x 800 x 3 = 1.5mb
			 */
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            @SuppressWarnings("UnnecessaryLocalVariable") int screenfulMemory = dm.heightPixels * dm.widthPixels * 3; // 3 = 24bpp

            // for full-size image, have one bitmap available for reuse
            mMaximumSize = screenfulMemory;
        }

        @Override
        synchronized public String memoryMetrics() {
            OSAssert.assertNotMainThread();

            return MoreObjects.toStringHelper(this)
                    .add("memorySize", mCurrentSize)
                    .add("maxMemorySize", mMaximumSize)
                    .add("bitmapCount", mRecycledBitmaps.size())
                    .toString();
        }

        @Override
        synchronized public void clear() {
            mRecycledBitmaps.clear();
            mLastCriteria = null;
            mCurrentSize = 0;
        }

        /**
         * Add the specified bitmap to the pool. This can be used to populate the pool or to add objects evicted through ways other than the
         * GC references.
         * <p/>
         */
        @Override
        synchronized public void add(Bitmap bmp) {
            OSAssert.assertNotMainThread();

            if (CacheServiceProvider.get().getUsingLargeArtwork()) {
                return;
            }

            boolean added = false;
            Criteria criteria = newCriteria(bmp);
            if (criteria != null) {
                final int bmpSize = BitmapTools.bitmapSize(bmp);

                // target size is the maximum allowed size minus the current bitmap's size
                final int targetSize = mMaximumSize - bmpSize;

                if (mCurrentSize > targetSize) {
                    // only remove bitmaps that don't match the current criteria being read

                    // copy of criteria set since we're changing the collection
                    List<Criteria> allCriteria = new ArrayList<>(mRecycledBitmaps.keySet());
                    for (Criteria c : allCriteria) {
                        if (Objects.equal(c, mLastCriteria)) {
                            // skip keys/criteria that match the last used criteria
                            continue;
                        }

                        // get list of bitmaps in this bucket
                        ListIterator<Bitmap> iterator = mRecycledBitmaps.get(c).listIterator();
                        while (mCurrentSize > targetSize && iterator.hasNext()) {

                            Bitmap removeBitmap = iterator.next();
                            iterator.remove();

                            // update current size
                            mCurrentSize -= BitmapTools.bitmapSize(removeBitmap);

                            // forcibly recycle this bitmap now
                            removeBitmap.recycle();
                        }
                    }
                }
                if (mCurrentSize <= targetSize) {
                    // we made enough room
                    mRecycledBitmaps.put(criteria, bmp);
                    mCurrentSize += bmpSize;
                    added = true;
                }
            }
            if (!added) {
                // worse case we just directly recycle the bitmap
                bmp.recycle();
            }
        }

        /**
         * Return a BitmapReference object for the specified criteria, if it exists in the recycler. The wrapped bitmap will AUTOMATICALLY
         * be returned to the pool when the BitmapReference object is eligible for garbage collection, so calling code MUST retain a
         * reference until the Bitmap is no longer needed.
         *
         * @param criteria the criteria we're looking for
         * @return a recyclable bitmap, optionally
         */
        @Override
        @Nullable
        synchronized public RecyclableBitmap get(Criteria criteria) {
            OSAssert.assertNotMainThread();

            // store the last bitmap type that we've requested so we can make intelligent decisions about evicting the bitmap cache
            mLastCriteria = criteria;

            List<Bitmap> existing = mRecycledBitmaps.get(criteria);
            if (!existing.isEmpty()) {
                Bitmap bmp = existing.remove(0);

                // reduce the size of the bitmap pool appropriately, until it is re-added
                mCurrentSize -= BitmapTools.bitmapSize(bmp);

                return newRecyclableBitmap(bmp);
            } else {
                return null;
            }
        }

        /**
         * convert a bitmap to a recyclablebitmap. When the RecyclableBitmap is eligible for GC, it is automatically pooled.
         */
        @Override
        @Nonnull
        public RecyclableBitmap newRecyclableBitmap(Bitmap bmp) {
            return RecyclableBitmap.newInstance(bmp);
        }
    }

    /**
     * base implementation of criteria
     */
    @Immutable
    private static class CriteriaImpl implements Criteria {
        final private int mWidth;
        final private int mHeight;

        CriteriaImpl(int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        @Override
        public int hashCode() {
            return ((mWidth * 31) + mHeight) * 31;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (o == null) {
                return false;
            }
            if (o == this) {
                return true;
            }
            if (!(o instanceof CriteriaImpl)) {
                return false;
            }
            CriteriaImpl other = (CriteriaImpl) o;
            return other.mHeight == mHeight && other.mWidth == mWidth;
        }
    }
}
