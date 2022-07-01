/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.google.common.base.Optional;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.cache.CacheEntry;
import com.orangebikelabs.orangesqueeze.cache.CacheFuture;
import com.orangebikelabs.orangesqueeze.cache.CacheService;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.cache.CachedItemInvalidException;
import com.orangebikelabs.orangesqueeze.cache.CachedItemNotFoundException;
import com.orangebikelabs.orangesqueeze.cache.CachedItemStatusException;
import com.orangebikelabs.orangesqueeze.cache.SBCacheException;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.Closeables;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.Drawables;
import com.orangebikelabs.orangesqueeze.common.ListJobProcessor;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class ThumbnailProcessor extends ListJobProcessor<View> {

    public static final int LOAD_IMAGE_POST_DELAY = 100;

    // this should definitely be the UI thread
    final static protected Handler sHandler = new Handler(Looper.getMainLooper());

    final protected Context mContext;
    final protected CacheService mCacheService;

    final protected ConcurrentMap<ImageView, BitmapJob> mBitmapJobs = new MapMaker().concurrencyLevel(2).initialCapacity(8).makeMap();
    final protected ConcurrentMap<ImageView, CacheEntry> mLoadedJobs = new MapMaker().concurrencyLevel(2).weakKeys().makeMap();
    final protected Drawable mLoadingDrawable;
    final protected Drawable mNoArtworkDrawable;
    final protected boolean mArtistArtworkDisabled;
    final protected String mItemIconDescription;
    final protected String mItemIconMissingDescription;

    volatile protected int mThumbnailPreloadWidth;

    public ThumbnailProcessor(Context context) {
        OSAssert.assertMainThread();

        mContext = context;
        mThumbnailPreloadWidth = 0;

        mCacheService = CacheServiceProvider.get();
        mNoArtworkDrawable = Drawables.getNoArtworkDrawableTinted(context);
        mLoadingDrawable = Drawables.getLoadingDrawable(context);
        mArtistArtworkDisabled = SBPreferences.get().isArtistArtworkDisabled();
        mItemIconDescription = mContext.getString(R.string.item_icon_desc);
        mItemIconMissingDescription = mContext.getString(R.string.item_icon_missing_desc);
    }

    @Nonnull
    public Context getContext() {
        return mContext;
    }

    /**
     * initiates a preload job.
     * <p/>
     * Need NOT be called on the main thread.
     */
    public void addArtworkPreloadJob(String coverId, ArtworkType imageType) {

        OSAssert.assertNotNull(coverId, "no null coverid");
        OSAssert.assertTrue(!coverId.equals(""), "no blank coverid");

        int width = mThumbnailPreloadWidth;

        if (!isRunning() || width == 0) {
            return;
        }

        // this signifies a preload situation
        addSecondaryJob(new PreloadJob(Artwork.newCacheRequest(mContext, coverId, imageType, width)));
    }

    /**
     * Adds an artwork retrieval job. If possible, complete the job in this thread but otherwise queue up a new job.
     * <p/>
     * Must be called on main thread.
     */
    public void addArtworkJob(ImageView iv, String coverId, ArtworkType imageType, ScaleType scaleType) {
        OSAssert.assertNotNull(coverId, "no null coverid");
        OSAssert.assertTrue(!coverId.equals(""), "no blank coverid");
        OSAssert.assertMainThread();

        if (!isRunning()) {
            setLoadingArtwork(iv);
            // bail if we're not running
            return;
        }

        if (mArtistArtworkDisabled && imageType == ArtworkType.ARTIST_THUMBNAIL) {
            // no artist artwork, just get rid of it
            setViewGone(iv);
            return;
        }

        int width;
        if (imageType.isThumbnail()) {
            width = iv.getWidth();
            if (width <= 0) {
                ViewGroup.LayoutParams params = iv.getLayoutParams();
                if (params != null && params.width > 0) {
                    width = params.width;
                }
            }
            if (width <= 0) {
                // assume width is for grid, which is the only dynamic view
                width = SBPreferences.get().getGridThumbnailWidth();
            }
            mThumbnailPreloadWidth = width;
        } else {
            CacheServiceProvider.get().aboutToDecodeLargeArtwork();
            width = Artwork.getFullSizeArtworkWidth(mContext);
        }

        if (width <= 0) {
            // couldn't determine the width
            return;
        }

        ArtworkCacheRequestCallback request = Artwork.newCacheRequest(mContext, coverId, imageType, width);
        CacheEntry newCacheEntry = request.getEntry();

        CacheEntry alreadyLoadedEntry = mLoadedJobs.put(iv, newCacheEntry);
        if (!newCacheEntry.equals(alreadyLoadedEntry)) {

            boolean artworkSet = false;
            // load not already in progress for this cache entry...
            try {
                ArtworkCacheData cacheData = mCacheService.peek(request).orNull();
                if (cacheData == null) {
                    setLoadingArtwork(iv, newCacheEntry);
                    addJob(iv, new LoadArtworkJob(iv, newCacheEntry, scaleType, request));
                } else {
                    RecyclableBitmap bmp = cacheData.decodeBitmap();
                    if (bmp == null) {
                        setLoadingArtwork(iv, newCacheEntry);
                        // we just need to decode the bitmap on a separate thread
                        addJob(iv, new DecodeBitmapJob(iv, newCacheEntry, scaleType, cacheData));
                    } else {
                        setArtwork(iv, bmp, scaleType);
                        // release the cache data
                        cacheData.close();
                    }
                }
                artworkSet = true;
            } catch (CachedItemStatusException e) {
                // indicates invalid or no artwork stored in cache
            } catch (IOException e) {
                OSLog.i(e.getMessage(), e);
            }

            // no artwork set at this point? an error occurred looking it up
            if (!artworkSet) {
                setNoArtwork(iv, newCacheEntry);
            }
        }
    }

    public void setLoadingArtwork(ImageView iv) {
        setLoadingArtwork(iv, null);
    }

    public void setNoArtwork(ImageView iv) {
        setNoArtwork(iv, null);
    }

    public void setViewGone(ImageView iv) {
        internalSetView(iv, null, null, null, null, View.GONE, mItemIconMissingDescription);
    }

    protected void setLoadingArtwork(ImageView iv, @Nullable CacheEntry entry) {
        internalSetView(iv, entry, mLoadingDrawable, null, ScaleType.CENTER_CROP, View.VISIBLE, "");
    }

    protected void setNoArtwork(ImageView iv, @Nullable CacheEntry entry) {
        internalSetView(iv, entry, mNoArtworkDrawable, null, ScaleType.CENTER_CROP, View.VISIBLE, mItemIconMissingDescription);
    }

    @Override
    protected void onNotRunning() {
        super.onNotRunning();

        sHandler.removeCallbacks(mProcessBitmapJobsRunnable);

        mBitmapJobs.clear();
        mLoadedJobs.clear();
    }

    public void resetPreloads() {
        // no implementation
    }

    /**
     * Remove any job queues tagged for the specific ImageView. Used when the ImageView is set from the normal draw thread.
     */
    public void setArtwork(ImageView iv, Drawable d, ScaleType scaleType) {
        internalSetView(iv, null, d, null, scaleType, View.VISIBLE, mContext.getString(R.string.item_icon_desc));
    }

    /**
     * Remove any job queues tagged for the specific ImageView. Used when the ImageView is set from the normal draw thread.
     */
    public void setArtwork(ImageView iv, RecyclableBitmap bmp, ScaleType scaleType) {
        internalSetView(iv, null, new BitmapDrawable(mContext.getResources(), bmp.get()), bmp, scaleType, View.VISIBLE, mContext.getString(R.string.item_icon_desc));
    }

    @Override
    public void removeJob(View jobEntity) {
        super.removeJob(jobEntity);

        if (jobEntity instanceof ImageView) {
            mLoadedJobs.remove(jobEntity);
            mBitmapJobs.remove(jobEntity);
        }
    }

    private void internalSetView(ImageView iv, @Nullable CacheEntry cacheEntry, @Nullable Drawable d, @Nullable RecyclableBitmap recyclableBitmap, @Nullable ScaleType scaleType, int visibility, String contentDescription) {
        OSAssert.assertMainThread();

        // this ImageView is properly initialized now, make sure any
        // work queues have no references to it
        removeJob(iv);

        iv.clearAnimation();
        if (d != null) {
            iv.setImageDrawable(d);
        }
        if (scaleType != null) {
            iv.setScaleType(scaleType);
        }
        iv.setVisibility(visibility);
        iv.setContentDescription(contentDescription);

        RecyclableBitmap.setInViewTag(iv, recyclableBitmap);

        if (cacheEntry != null) {
            mLoadedJobs.put(iv, cacheEntry);
        }
    }

    /**
     * spins through any pending drawable sets and initializes the imageviews
     */
    final protected Runnable mProcessBitmapJobsRunnable = new Runnable() {

        @Override
        public void run() {

            while (!mBitmapJobs.isEmpty()) {
                Iterator<Map.Entry<ImageView, BitmapJob>> it = mBitmapJobs.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<ImageView, BitmapJob> e = it.next();

                    ImageView iv = e.getKey();
                    BitmapJob job = e.getValue();
                    if (job.mAnimation == null) {
                        iv.clearAnimation();
                    } else {
                        iv.startAnimation(job.mAnimation);
                    }
                    iv.setImageDrawable(job.mDrawable);

                    RecyclableBitmap.setInViewTag(iv, job.mRecyclableBitmap);
                    // cache entry tag should already be set

                    it.remove();
                }

                // we might have other threads posting to update, but this one
                // will suffice
                sHandler.removeCallbacks(mProcessBitmapJobsRunnable);
            }
        }
    };

    class PreloadJob implements Job {
        @Nonnull
        final public ArtworkCacheRequestCallback mRequest;

        // preload artwork job
        PreloadJob(ArtworkCacheRequestCallback request) {
            mRequest = request;
        }

        @Override
        public boolean execute() {
            try {
                ArtworkCacheData data = mCacheService.load(mRequest, MoreExecutors.newDirectExecutorService()).checkedGet(Constants.READ_TIMEOUT,
                        Constants.TIME_UNITS);
                // do nothing with the artwork data, just release it now that it's in the cache
                data.close();
            } catch (CachedItemNotFoundException | InterruptedException | CachedItemInvalidException e) {
                // ignore for preloads
            } catch (IOException | TimeoutException | SBCacheException e) {
                OSLog.w(e.getMessage(), e);
            }
            return false;
        }

        @Override
        public void commit() {
            // never called
        }

        @Override
        public void abort() {
            // no implementation
        }
    }

    abstract class BaseArtworkJob implements Job {

        @Nonnull
        final public ScaleType mScaleType;

        @Nonnull
        final public ImageView mImageView;

        @Nullable
        final public CacheEntry mCacheEntry;

        // mutable, but accessed only on job thread
        @Nullable
        protected RecyclableBitmap mBitmapResult;


        // mutable, but accessed only on job thread
        @Nullable
        protected Drawable mDrawableResult;

        protected BaseArtworkJob(ImageView iv, @Nullable CacheEntry cacheEntry, ScaleType scaleType) {
            mImageView = iv;
            mScaleType = scaleType;
            mCacheEntry = cacheEntry;

        }

        @Nonnull
        protected Optional<Animation> getAnimation() {
            return Optional.of(AnimationUtils.loadAnimation(mContext, R.anim.thumbnail_fadein));
        }

        @Nonnull
        abstract protected ArtworkCacheData getArtworkCacheData() throws InterruptedException, SBCacheException, TimeoutException;

        @Override
        public boolean execute() {
            boolean commit = false;
            try {
                ArtworkCacheData data = getArtworkCacheData();
                try {
                    mBitmapResult = data.decodeBitmap();
                    commit = mBitmapResult != null;
                } finally {
                    data.close();
                }
            } catch (CachedItemNotFoundException|CachedItemInvalidException e) {
                mDrawableResult = mNoArtworkDrawable;
                commit = true;
            } catch (IOException | TimeoutException | SBCacheException e) {
                OSLog.w(e.getMessage(), e);
            } catch (InterruptedException e) {
                // ignore
            }
            return commit;
        }

        @Override
        public void commit() {
            // before we are ready to put in the view, load an animation object and get everything else ready
            Drawable d = null;
            RecyclableBitmap recyclableBitmap = null;
            if (mBitmapResult != null) {
                recyclableBitmap = mBitmapResult;
                mBitmapResult = null;

                d = new BitmapDrawable(mContext.getResources(), recyclableBitmap.get());
            } else if (mDrawableResult != null) {
                d = mDrawableResult;
                mDrawableResult = null;
            }

            if (d != null) {
                mBitmapJobs.put(mImageView, new BitmapJob(d, getAnimation().orNull(), recyclableBitmap, mCacheEntry));
                sHandler.postDelayed(mProcessBitmapJobsRunnable, LOAD_IMAGE_POST_DELAY);
            }
        }

        @Override
        public void abort() {
            mDrawableResult = null;

            if (mBitmapResult != null) {
                mBitmapResult.recycle();
                mBitmapResult = null;
            }
        }
    }

    /**
     * artwork job that requests the artwork from the cache
     */
    class LoadArtworkJob extends BaseArtworkJob {
        @Nonnull
        final public ArtworkCacheRequestCallback mRequest;

        // normal artwork job, already submitted
        LoadArtworkJob(ImageView iv, CacheEntry cacheEntry, ScaleType scaleType, ArtworkCacheRequestCallback request) {
            super(iv, cacheEntry, scaleType);
            mRequest = request;
        }

        @Nonnull
        @Override
        protected ArtworkCacheData getArtworkCacheData() throws InterruptedException, SBCacheException, TimeoutException {
            boolean success = false;
            CacheFuture<ArtworkCacheData> cacheFuture = mCacheService.load(mRequest, MoreExecutors.newDirectExecutorService());
            try {
                ArtworkCacheData data = cacheFuture.checkedGet(Constants.READ_TIMEOUT, Constants.TIME_UNITS);

                success = true;
                return data;
            } finally {
                if (!success) {
                    // clean up artwork cache data, we've already cancelled the request
                    Futures.addCallback(cacheFuture, Closeables.getCloserCallback(), MoreExecutors.directExecutor());
                }
            }
        }
    }

    /**
     * shortcut job that decodes artwork data already retrieved
     */
    class DecodeBitmapJob extends BaseArtworkJob {
        @Nonnull
        final public ArtworkCacheData mCacheData;

        // normal artwork job, already submitted
        DecodeBitmapJob(ImageView iv, CacheEntry cacheEntry, ScaleType scaleType, ArtworkCacheData cacheData) {
            super(iv, cacheEntry, scaleType);

            mCacheData = cacheData;
        }

        @Nonnull
        @Override
        protected ArtworkCacheData getArtworkCacheData() throws InterruptedException, SBCacheException, TimeoutException {
            return mCacheData;
        }
    }

    static class BitmapJob {

        BitmapJob(Drawable d, @Nullable Animation anim, @Nullable RecyclableBitmap recyclableBitmap, @Nullable CacheEntry entry) {
            mDrawable = d;
            mAnimation = anim;
            mRecyclableBitmap = recyclableBitmap;
            mCacheEntry = entry;
        }

        @Nonnull
        final Drawable mDrawable;

        @Nullable
        final Animation mAnimation;

        @Nullable
        final RecyclableBitmap mRecyclableBitmap;

        @Nullable
        final CacheEntry mCacheEntry;
    }
}
