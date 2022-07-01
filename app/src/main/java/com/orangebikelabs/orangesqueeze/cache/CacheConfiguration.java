/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.cache;

import android.app.ActivityManager;
import android.content.Context;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.Uninterruptibles;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.CacheLocation;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.FileUtils;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.event.AppPreferenceChangeEvent;
import com.squareup.otto.Subscribe;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Class instance used to house the cache configuration information.
 * <p/>
 * Cache configuration is final because it received notifications from BusProvider.
 *
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
final public class CacheConfiguration {

    final public static int SQLITE_CACHE_SIZE = 40 * Constants.MB;
    final public static float MEMORY_CACHE_FACTOR = 0.10f;

    final private Context mContext;

    final private AtomicReference<File> mExpandedCacheDir = new AtomicReference<>();

    final private AtomicInteger mMaxMemorySize = new AtomicInteger();

    final private AtomicInteger mMaxExternalSize = new AtomicInteger();

    // accessed from main thread only
    private boolean mReceiverRegistered = false;

    final private CountDownLatch mInitLatch = new CountDownLatch(1);

    public CacheConfiguration(Context context) {
        mContext = context;

        OSAssert.assertApplicationContext(context);
        refresh();
    }

    @Nonnull
    public File getExpandedCacheDir() {
        Uninterruptibles.awaitUninterruptibly(mInitLatch);
        return mExpandedCacheDir.get();
    }

    public int getMaxExternalSize() {
        Uninterruptibles.awaitUninterruptibly(mInitLatch);
        return mMaxExternalSize.get();
    }

    public int getMaxMemorySize() {
        Uninterruptibles.awaitUninterruptibly(mInitLatch);
        return mMaxMemorySize.get();
    }

    public int getMaxSqliteSize() {
        return SQLITE_CACHE_SIZE;
    }

    public void refresh() {
        OSExecutors.getUnboundedPool().execute(this::internalRefresh);
    }

    synchronized private void internalRefresh() {
        File baseCacheDir = null;
        SBPreferences prefs = SBPreferences.get();
        if (prefs.getCacheLocation() == CacheLocation.EXTERNAL) {
            // use external location for cache files
            baseCacheDir = mContext.getExternalCacheDir();
            if (baseCacheDir == null || !baseCacheDir.exists()) {
                OSLog.i(Tag.CACHE, "External cache storage unavailable, resorting to internal storage");
            }
        }
        if (baseCacheDir == null) {
            // fall back to internal location
            baseCacheDir = mContext.getCacheDir();
        }
        mExpandedCacheDir.set(new File(baseCacheDir, "OpenSqueezeCache"));
        if (!mExpandedCacheDir.get().exists()) {
            FileUtils.mkdirsChecked(mExpandedCacheDir.get());
        }

        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        OSAssert.assertNotNull(am, "activitiymanager can't be null");

        // to keep the app running, set a realistic max to memory cache
        mMaxMemorySize.set((int) (am.getMemoryClass() * Constants.MB * MEMORY_CACHE_FACTOR));

        int minStorageCacheSize = mContext.getResources().getInteger(R.integer.min_coverart_storage_cache_size) * Constants.MB;
        int desiredStorageCacheSize = prefs.getCacheStorageSize() * Constants.MB;
        if (desiredStorageCacheSize <= 0) {
            int maxStorageCacheSize = mContext.getResources().getInteger(R.integer.max_coverart_storage_cache_size) * Constants.MB;
            long totalSpace = FileUtils.getTotalSpace(mExpandedCacheDir.get());
            float storageCacheFactor = mContext.getResources().getFraction(R.fraction.coverart_storage_cache_factor, 1, 1);
            long calcSpace = (long) (totalSpace * storageCacheFactor);
            desiredStorageCacheSize = (int) Math.min(calcSpace, maxStorageCacheSize);
        }
        mMaxExternalSize.set(Math.max(minStorageCacheSize, desiredStorageCacheSize));

        mInitLatch.countDown();
    }

    public void listenTo(ServiceManager manager) {
        manager.addListener(mServiceListener, OSExecutors.getMainThreadExecutor());
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenAppPreferenceChanges(AppPreferenceChangeEvent event) {
            refresh();
        }
    };

    final private ServiceManager.Listener mServiceListener = new ServiceManager.Listener() {

        @Override
        public void failure(@Nullable Service service) {
            // intentionally blank
        }

        @Override
        public void healthy() {
            if (!mReceiverRegistered) {
                BusProvider.getInstance().register(mEventReceiver);
                mReceiverRegistered = true;
            }
            refresh();
        }

        @Override
        public void stopped() {
            if (mReceiverRegistered) {
                BusProvider.getInstance().unregister(mEventReceiver);
                mReceiverRegistered = false;
            }
        }
    };
}
