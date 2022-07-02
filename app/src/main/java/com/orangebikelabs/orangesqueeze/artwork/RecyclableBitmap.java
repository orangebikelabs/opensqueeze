/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.graphics.Bitmap;
import android.view.View;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.RefCount;
import com.orangebikelabs.orangesqueeze.common.ThreadTools;
import com.orangebikelabs.orangesqueeze.common.MoreViews;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Recyclable bitmaps have two levels of recycling available:
 * <p/>
 * First, if the reference count on the bitmap image ever drops to zero then the bitmap is automatically added to the bitmap recycler. This
 * is the most efficient and immediate way to recycle a bitmap.
 * <p/>
 * Second, if a recyclablebitmap object ever becomes weakly reference, the underlying bitmap object is automatically added to the bitmap
 * recycler. Because of the nature of garbage collection, this often occurs in bursts when the GC is active.
 *
 * @author tsandee
 */
abstract public class RecyclableBitmap {

    public static void setInViewTag(View view, @Nullable RecyclableBitmap bmp) {
        RecyclableBitmap oldValue = (RecyclableBitmap) MoreViews.getAndSetTag(view, R.id.tag_recyclable, bmp);
        if (oldValue != null) {
            oldValue.recycle();
        }
    }

    /**
     * return a non-recyclable instance. on platforms prior to HONEYCOMB, this is the only type of recyclable bitmap available
     */
    @Nonnull
    public static RecyclableBitmap newNonRecyclableInstance(Bitmap bmp) {
        return new NonRecyclableBitmap(bmp);
    }

    /**
     * return an instance that performs simple recycling of the bitmap using Bitmap.recycle()
     */
    @Nonnull
    public static RecyclableBitmap newSimpleRecyclableInstance(Bitmap bmp) {
        return new SimpleRecyclableBitmapImpl(bmp);
    }

    /**
     * return a normal recyclable bitmap
     */
    @Nonnull
    public static RecyclableBitmap newInstance(Bitmap bmp) {
        return new DefaultRecyclableBitmapImpl(bmp);
    }

    @Nullable
    @GuardedBy("this")
    protected Bitmap mBitmap;

    @Nonnull
    final protected RefCount mRefCount = RefCount.newInstance("bitmap", 1);

    protected RecyclableBitmap(@Nullable Bitmap bmp) {
        mBitmap = bmp;
    }

    /**
     * indicate that the caller is done with this instance
     */
    abstract public void recycle();

    /**
     * retrieve the Bitmap object
     */
    @Nonnull
    synchronized public Bitmap get() {
        Bitmap bmp = mBitmap;
        if (bmp == null) {
            throw new IllegalStateException();
        }
        return bmp;
    }

    @Override
    public boolean equals(Object o) {
        // use object equality
        return o == this;
    }

    @Override
    public int hashCode() {
        // use identity hashcode
        return System.identityHashCode(this);
    }

    /**
     * increment the reference count
     */
    public void incrementRefCount() {
        if (mRefCount.increment()) {
            throw new IllegalStateException("refcount was already zero");
        }
    }

    @ThreadSafe
    static private class DefaultRecyclableBitmapImpl extends RecyclableBitmap {

        @GuardedBy("this")
        private boolean mRecyclable;

        protected DefaultRecyclableBitmapImpl(Bitmap bmp) {
            super(bmp);

            mRecyclable = true;
        }

        @Override
        public void recycle() {
            if (mRefCount.decrement()) {
                Bitmap shouldRecycleBitmap;

                synchronized (this) {
                    shouldRecycleBitmap = mBitmap;
                    mBitmap = null;
                }
                if (shouldRecycleBitmap != null && mRecyclable) {
                    if (ThreadTools.isMainThread()) {
                        final Bitmap fBmp = shouldRecycleBitmap;
                        // recycle the bitmap off the main thread since it's a very synchronized data structure
                        OSExecutors.getUnboundedPool().execute(() -> BitmapRecycler.sRecyclerInstance.get().add(fBmp));
                    } else {
                        BitmapRecycler.sRecyclerInstance.get().add(shouldRecycleBitmap);
                    }
                }
            }
        }
    }

    @ThreadSafe
    static private class SimpleRecyclableBitmapImpl extends RecyclableBitmap {

        protected SimpleRecyclableBitmapImpl(Bitmap bmp) {
            super(bmp);
        }

        @Override
        public void recycle() {
            if (mRefCount.decrement()) {
                synchronized (this) {
                    if (mBitmap != null) {
                        mBitmap.recycle();
                        mBitmap = null;
                    }
                }
            }
        }
    }

    static private class NonRecyclableBitmap extends RecyclableBitmap {

        protected NonRecyclableBitmap(Bitmap bmp) {
            super(bmp);
        }

        @Override
        public void recycle() {
            // do nothing
        }
    }

}
