/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import android.app.PendingIntent;
import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Queues;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.Uninterruptibles;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.NotificationCommon;
import com.orangebikelabs.orangesqueeze.artwork.BitmapRecycler;
import com.orangebikelabs.orangesqueeze.common.Closeables;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.FileUtils;
import com.orangebikelabs.orangesqueeze.common.InterruptedAwareRunnable;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.ThreadTools;
import com.orangebikelabs.orangesqueeze.compat.Compat;
import com.orangebikelabs.orangesqueeze.database.DatabaseWriterThreadPoolExecutor;
import com.orangebikelabs.orangesqueeze.ui.MainActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_EXPIRES_TIMESTAMP;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_SERVERSCAN_TIMESTAMP;

/**
 * @author tsandee
 */
public class CacheService {
    /**
     * the cache version identifier. If the backing cache format changes in an incompatible way this will be bumped and old cache items will
     */
    private static final int CACHE_VERSION = 6;

    /**
     * number of items in the renew queue before they are committed to the database
     */
    final private static int MAX_ITEM_RENEW_THRESHOLD = 200;

    // don't rely on mRenewItems.size() because it's an O(n) operation
    final private AtomicInteger mRenewItemCount = new AtomicInteger();

    // fast, concurrent list of items to renew
    final private ConcurrentLinkedQueue<CacheEntry> mRenewItems = Queues.newConcurrentLinkedQueue();

    /**
     * the current configuration. This is observable to receive notifications on changes.
     */
    final private CacheConfiguration mConfiguration;

    /**
     * the service is actually started on stopped here
     */
    final private AtomicReference<ServiceManager> mServiceManager = new AtomicReference<>();

    /**
     * whether or not we have triggered conditions that require a cleanup
     */
    final protected AtomicBoolean mCleanupNeeded = new AtomicBoolean();

    /**
     * whether or not we are actively wiping the cache
     */
    final protected AtomicBoolean mWiping = new AtomicBoolean();

    /**
     * the application context
     */
    final private Context mApplicationContext;

    /**
     * Access to the database through this wrapper
     */
    private CacheDatabase mBlockedCacheDatabase;

    /**
     * a fixed-size memory cache
     */
    private MemoryCache mBlockedMemoryCache;

    /**
     * executor to use for database access
     */
    private ListeningExecutorService mBlockedDatabaseExecutor;

    /**
     * The number of activities on the stack using large artwork
     */
    final private AtomicInteger mUsingLargeArtwork = new AtomicInteger(0);

    final private CountDownLatch mInitLatch = new CountDownLatch(1);

    public CacheService(Context context) {
        OSAssert.assertApplicationContext(context);

        mApplicationContext = context;
        mConfiguration = new CacheConfiguration(context);

        OSExecutors.getUnboundedPool().execute(() -> {
            mBlockedCacheDatabase = new CacheDatabase(context, mConfiguration);
            mBlockedDatabaseExecutor = DatabaseWriterThreadPoolExecutor.newInstance(20, "Cache Service Database Writer");
            mBlockedMemoryCache = new MemoryCache(this, mConfiguration);
            mInitLatch.countDown();
        });
    }

    public void setUsingLargeArtwork(boolean usingLargeArtwork) {
        int val;
        if (usingLargeArtwork) {
            val = mUsingLargeArtwork.incrementAndGet();
        } else {
            val = mUsingLargeArtwork.decrementAndGet();
        }

        if (val > 0) {
            aboutToDecodeLargeArtwork();
        }
    }

    public boolean getUsingLargeArtwork() {
        return mUsingLargeArtwork.get() > 0;
    }

    public void aboutToDecodeLargeArtwork() {
        BitmapRecycler.getInstance(mApplicationContext).clear();
    }

    @Nonnull
    public String memoryMetrics() {
        BitmapRecycler recycler = BitmapRecycler.getInstance(mApplicationContext);

        return MoreObjects.toStringHelper("CacheService::MemoryMetrics")
                .add("renewItemCount", mRenewItems.size())
                .add("memoryCacheSize", getMemoryCache().memorySize())
                .add("bitmapRecyclerCacheMetrics", recycler.memoryMetrics())
                .toString();
    }

    /**
     * Callers MUST retain a reference to this instance, or the backing file will be deleted.
     */
    @Nonnull
    public ManagedTemporary createManagedTemporary() throws IOException {
        return ManagedTemporaryImpl.newInstance(mConfiguration);
    }

    @Nonnull
    public CacheConfiguration getConfiguration() {
        return mConfiguration;
    }

    public boolean isRunning() {
        return mServiceManager.get() != null && !mWiping.get();
    }

    public boolean isStopping() {
        ServiceManager sm = mServiceManager.get();
        if (sm == null) return true;

        return !sm.isHealthy();
    }

    public void start() {
        final ServiceManager manager = new ServiceManager(Collections.singletonList(new CleanupService()));

        if (mServiceManager.compareAndSet(null, manager)) {

            manager.addListener(new ServiceManager.Listener() {
                @Override
                public void healthy() {
                }

                @Override
                public void stopped() {
                    mServiceManager.compareAndSet(manager, null);
                }

                @Override
                public void failure(@Nullable Service service) {
                    mServiceManager.compareAndSet(manager, null);
                }
            }, MoreExecutors.newDirectExecutorService());

            mConfiguration.listenTo(manager);
            getMemoryCache().listenTo(manager);

            manager.startAsync();
        }
    }

    public void stop() {
        ServiceManager manager = mServiceManager.getAndSet(null);
        if (manager != null) {
            manager.stopAsync();
        }
    }

    public void triggerReleaseMemory() {
        getMemoryCache().clear();
        BitmapRecycler.getInstance(mApplicationContext).clear();
    }

    public void triggerWipe() {
        OSExecutors.getUnboundedPool().execute(this::wipe);
    }

    public void wipe() {

        NotificationCommon.INSTANCE.createNotificationChannels(mApplicationContext);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mApplicationContext);

        if (!mWiping.compareAndSet(false, true)) {
            // already wiping
            return;
        }
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mApplicationContext, NotificationCommon.CACHE_NOTIFICATION_CHANNEL_ID);
            builder.setOngoing(true);
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
            builder.setContentTitle(mApplicationContext.getString(R.string.app_name));
            builder.setContentText(mApplicationContext.getString(R.string.wipingcache_notification_text));
            builder.setSmallIcon(R.drawable.ic_notification);
            builder.setContentIntent(PendingIntent.getActivity(mApplicationContext, 0,
                    MainActivity.Companion.newIntent(mApplicationContext),
                    Compat.getDefaultPendingIntentFlags() | PendingIntent.FLAG_UPDATE_CURRENT));

            notificationManager.notify(Constants.NOTIFICATIONID_CACHEWIPE, builder.build());

            // not required that the service be running for this method to execute
            getMemoryCache().clear();

            // clear any bitmaps too
            BitmapRecycler.getInstance(mApplicationContext).clear();

            getDatabase().wipe();
        } finally {
            notificationManager.cancel(Constants.NOTIFICATIONID_CACHEWIPE);
            mWiping.set(false);
        }
    }

    public boolean remove(CacheEntry entry) {
        boolean found = getMemoryCache().remove(entry);

        if (getDatabase().removeFromDatabase(entry)) {
            found = true;
        }
        return found;
    }

    public <T, C> Optional<T> peek(final CacheRequestCallback<T, C> request) throws CachedItemInvalidException, CachedItemNotFoundException {
        if (!isRunning()) {
            return Optional.absent();
        }

        final CacheEntry entry = request.getEntry();
        // we're only trying the memory
        try {
            T retval = null;
            C memCacheValue = getMemoryCache().get(entry);
            if (memCacheValue != null) {
                retval = request.onAdaptFromMemoryCache(this, memCacheValue);
                // trigger row renewal
                renew(request, entry, false);
            }
            return Optional.fromNullable(retval);
        } catch (Exception e) {
            Throwables.propagateIfPossible(e, CachedItemInvalidException.class, CachedItemNotFoundException.class);

            OSLog.w(Tag.CACHE, "CacheService.peek(): " + e.getMessage(), e);
            return Optional.absent();
        }
    }

    @Nonnull
    public <T, C> CacheFuture<T> load(final CacheRequestCallback<T, C> request, ListeningExecutorService executorService) {
        if (!isRunning()) {
            // if cache isn't running, execute the request anyway but uncached
            return CacheFutureFactory.create(executorService.submit(() -> {
                try {
                    return loadNotRunning(request);
                } catch (InterruptedIOException e) {
                    // change interrupted io to interrupted for consistency's sake
                    throw new InterruptedException();
                }
            }));
        }

        final CacheEntry entry = request.getEntry();
        if (entry.tryLockNonBlocking()) {
            try {
                // first try from memory
                C memCacheValue = getMemoryCache().get(entry);
                if (memCacheValue != null) {
                    T retval = request.onAdaptFromMemoryCache(this, memCacheValue);

                    // trigger row renewal
                    renew(request, entry, false);

                    // and return value immediately
                    return CacheFutureFactory.immediateFuture(retval);
                }
            } catch (CachedItemNotFoundException | CachedItemInvalidException e) {
                return CacheFutureFactory.immediateFailedFuture(e);
            } catch (IOException e) {
                // IOException adapting from memory cache, try loading directly
                OSLog.w(Tag.CACHE, "CacheService.load(): " + e.getMessage(), e);
            } finally {
                entry.releaseLock();
            }
        }

        // create and return future for the cache item
        return CacheFutureFactory.create(executorService.submit(() -> {
            try {
                return loadBlocking(request);
            } catch (CachedItemStatusException e) {
                // if the request wants us to do it, mark the item as invalid in case of failure
                if (request.shouldMarkFailedRequests()) {
                    switch (e.getItemStatus()) {
                        case INVALID:
                            getMemoryCache().markInvalid(entry);
                            break;
                        case NOTFOUND:
                            getMemoryCache().markMissing(entry);
                            break;
                        default:
                            throw new IllegalStateException("Cannot set entry status to " + e.getItemStatus());
                    }
                    getDatabase().markEntry(entry, e.getItemStatus());
                }
                throw e;
            } catch (InterruptedIOException e) {
                // change interrupted io to interrupted for consistency's sake
                throw new InterruptedException();
            }
        }));
    }

    @Nonnull
    protected <T, C> T loadNotRunning(final CacheRequestCallback<T, C> request) throws SBCacheException, IOException, InterruptedException {
        OSLog.v(Tag.CACHE, "Executing request without cache");
        return request.onLoadData(this);
    }

    @Nonnull
    protected <T, C> T loadBlocking(final CacheRequestCallback<T, C> request) throws InterruptedException, TimeoutException, SBCacheException, IOException {
        final CacheEntry entry = request.getEntry();
        if (!entry.tryLock(Constants.READ_TIMEOUT, Constants.TIME_UNITS)) {
            throw new TimeoutException("Waiting for lock on cache entry");
        }

        // list of values that we should potentially clean up
        Set<Object> cleanupValues = new LinkedHashSet<>();

        // we've acquired the permit, now use this variable to determine whether we release it or not
        try {
            String hitSource = null;
            boolean performRowRenewal = false;

            T retval;
            // first try from memory again
            C memCacheValue = getMemoryCache().get(entry);
            if (memCacheValue != null) {
                retval = request.onAdaptFromMemoryCache(this, memCacheValue);
                hitSource = "MEMORY";
                performRowRenewal = true;
            } else {
                // ok, try from the database
                retval = loadFromDatabase(request, entry);
                if (retval != null) {
                    // found
                    hitSource = "DATABASE";

                    // add this to candidate list of items to clean up
                    cleanupValues.add(retval);

                    performRowRenewal = true;
                } else {

                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }

                    // we ignore this and continue on with remote retrieval
                    retval = request.onLoadData(this);

                    // add this to candidate list of items to clean up
                    cleanupValues.add(retval);

                    // on success, store the value in the cache
                    storeToDatabase(entry, request, retval);
                }

                // update the memory cache, which will likely alter the value
                T newRetVal = storeToMemory(entry, request, retval);
                if (newRetVal != retval) {
                    // in case retval changed
                    retval = newRetVal;
                    cleanupValues.add(newRetVal);
                }
            }
            // when loaded from a cache, trigger the row renewal
            if (performRowRenewal) {
                renew(request, entry, false);
            }
            if (OSLog.isLoggable(Tag.CACHE, OSLog.VERBOSE)) {
                if (hitSource != null) {
                    OSLog.v(Tag.CACHE, "BLOCKING_HIT " + hitSource + " " + entry + "=" + retval);
                } else {
                    OSLog.v(Tag.CACHE, "BLOCKING_LOAD " + entry + "=" + retval);
                }
            }

            // don't clean this value up, we're returning it
            cleanupValues.remove(retval);

            return retval;
        } finally {
            entry.releaseLock();

            // clean up any values that aren't being returned
            for (Object o : cleanupValues) {
                Closeables.close(o);
            }
        }
    }

    @Nonnull
    private CacheDatabase getDatabase() {
        Uninterruptibles.awaitUninterruptibly(mInitLatch);
        return mBlockedCacheDatabase;
    }

    @Nonnull
    private ListeningExecutorService getDatabaseExecutor() {
        Uninterruptibles.awaitUninterruptibly(mInitLatch);
        return mBlockedDatabaseExecutor;
    }

    @Nonnull
    private MemoryCache getMemoryCache() {
        Uninterruptibles.awaitUninterruptibly(mInitLatch);
        return mBlockedMemoryCache;
    }

    /**
     * returns null if cache entry not found but processing should continue normally
     */
    @Nullable
    private <T, C> T loadFromDatabase(CacheRequestCallback<T, C> request, CacheEntry entry) throws SBCacheException {
        String extraSelection;
        String extraArg;

        switch (entry.getCacheType()) {
            case SERVERSCAN: {
                Long lastScan = SBContextProvider.get().getServerStatus().getLastScanTime();
                if (lastScan == null) {
                    // mid-scan, won't find it
                    return null;
                }
                extraSelection = COLUMN_CACHE_SERVERSCAN_TIMESTAMP + " = ?";
                extraArg = lastScan.toString();
                break;
            }
            case TIMEOUT:
                extraSelection = COLUMN_CACHE_EXPIRES_TIMESTAMP + " > ?";
                extraArg = Long.toString(System.currentTimeMillis());
                break;
            default:
                throw new IllegalStateException();
        }

        try {
            return internalLoadFromDatabase(request, entry, extraSelection, extraArg);
        } catch (FileNotFoundException e) {
            throw new CachedItemNotFoundException("Expansion file purged by system");
        } catch (IOException e) {
            throw new CachedItemInvalidException("Problem adapting cached data", e);
        }
    }

    /**
     * returns the object that object that should be handed back to the client
     */
    @Nonnull
    private <T, C> T storeToMemory(CacheEntry entry, CacheRequestCallback<T, C> request, T data) {
        // now adapt this for the memory cache
        T retval = data;
        try {
            C adapted = request.onAdaptForMemoryCache(this, data);
            if (adapted != null) {
                getMemoryCache().put(entry, request, adapted);
                retval = request.onAdaptFromMemoryCache(this, adapted);
            }
        } catch (IOException e) {
            OSLog.w(Tag.CACHE, "Error adapting record for memory cache", e);
        }
        return retval;
    }

    /**
     * perform "renew" operation, which resets the last accessed timestamps
     */
    private void cleanupRenewItems() {
        int renewCount = 0;
        if (!mRenewItems.isEmpty()) {
            CacheDatabase.EntryRenewer renewer = getDatabase().newEntryRenewer();
            try {
                try {
                    CacheEntry renewItem;
                    while ((renewItem = mRenewItems.poll()) != null) {
                        renewer.renewEntry(renewItem);
                    }
                    renewer.commit();
                } finally {
                    renewer.close();
                }
            } catch (IOException e) {
                OSLog.w(Tag.CACHE, "Error cleaning up renewable items", e);
            }

            // in one fell swoop, get rid of the proper number of items
            mRenewItemCount.addAndGet(-renewer.getUpdateCount());
            renewCount = renewer.getRenewCount();
        }
        OSLog.d(OSLog.Tag.CACHE, renewCount + " item(s) were renewed in cache");
    }

    /**
     * returns the appropriate cached representation for storage in the memory cache
     */
    private <T, C> void storeToDatabase(CacheEntry entry, CacheRequestCallback<T, C> request, T data) {
        try {
            AtomicLong sizeEstimate = new AtomicLong();
            ByteSource byteSource = request.onSerializeForDatabaseCache(this, data, sizeEstimate);
            if (byteSource != null) {
                getDatabase().storeEntry(getDatabaseExecutor(), entry, byteSource, sizeEstimate.longValue(), getFutureCacheTimeout());
            }
        } catch (IOException e) {
            OSLog.w(Tag.CACHE, "Error writing record to cache", e);
        }
    }

    protected void checkCacheVersion() {
        File cacheFingerprintFile = new File(mConfiguration.getExpandedCacheDir(), "cache.fingerprint." + CACHE_VERSION);
        if (!cacheFingerprintFile.isFile()) {
            // perform these tasks to prepare for a new cache directory
            try {
                getDatabase().wipe();

                Files.createParentDirs(cacheFingerprintFile);
                boolean result = cacheFingerprintFile.createNewFile();
                if (!result) {
                    OSLog.w(Tag.CACHE, "Cache fingerprint file already exists");
                }
            } catch (IOException e) {
                OSLog.w(Tag.CACHE, "Error creating cache fingerprint file " + cacheFingerprintFile.getPath() + ".  Permissions problem?", e);
            }
        }
    }

    /**
     * called on startup. This cleans up temporary files that match a specific pattern.
     */
    protected void cleanupManagedTemporaryFiles() {
        final boolean verbose = OSLog.isLoggable(Tag.CACHE, OSLog.VERBOSE);
        File[] files = mConfiguration.getExpandedCacheDir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (verbose) {
                    OSLog.v(Tag.CACHE, "Cache cleanup: Discovered file " + f);
                }
                if (f.getName().endsWith(".tmp")) {
                    OSLog.i(Tag.CACHE, "Cache cleanup: Deleting tempfile cruft " + f);
                    FileUtils.deleteChecked(f);
                }
            }
        }
    }

    /**
     * return the timestamp in the future when cache entries will expire
     */
    public long getFutureCacheTimeout() {
        // timeout one hour in future, don't use TimeUnit.MINUTES or HOURS because it's missing on some android versions
        long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60 * 60);
        //DEBUG long timeout = System.currentTimeMillis() + 60 * 1000;
        return timeout;
    }

    @Nullable
    private <T, C> T internalLoadFromDatabase(CacheRequestCallback<T, C> request, CacheEntry entry, String extraSelection, String extraArg) throws IOException, SBCacheException {
        ByteSource byteSource = getDatabase().loadEntry(entry, extraSelection, extraArg).orNull();

        T retval = null;
        if (byteSource != null) {
            retval = request.onDeserializeCacheData(this, byteSource, byteSource.size());
        }
        return retval;
    }

    private void renew(CacheRequestCallback<?, ?> request, CacheEntry entry, boolean nonBlocking) {

        mRenewItems.add(entry);

        // when we cross the threshold, trigger a cleanup
        boolean cleanupNeeded = mRenewItemCount.getAndIncrement() >= MAX_ITEM_RENEW_THRESHOLD;
        if (cleanupNeeded) {

            // set the global flag

            if (!mCleanupNeeded.getAndSet(true)) {
                // if this is called from the main thread and the fixed queue in the database executor is full
                // then callerruns policy would trigger database update on the main thread (bad!)

                if (ThreadTools.isMainThread()) {
                    OSExecutors.getSingleThreadScheduledExecutor().execute(mCleanupRunnable);
                } else {
                    getDatabaseExecutor().execute(mCleanupRunnable);
                }
            }
        }
    }


    /**
     * service instance that handles startup/shutdown of the cache service
     */
    class CleanupService extends AbstractScheduledService {
        @Override
        @Nonnull
        protected ScheduledExecutorService executor() {
            // don't use database executor, we don't want startup/shutdown operations to be deferred due to scrolling
            return OSExecutors.getSingleThreadScheduledExecutor();
        }

        @Override
        @Nonnull
        protected String serviceName() {
            return "CleanupService";
        }

        @Override
        @Nonnull
        protected void runOneIteration() {
            // run the cleanup on the database executor
            getDatabaseExecutor().execute(mCleanupRunnable);
        }

        @Override
        protected Scheduler scheduler() {
            return Scheduler.newFixedRateSchedule(Constants.CACHEMAINTENANCE_DELAY, Constants.CACHEMAINTENANCE_INTERVAL, Constants.TIME_UNITS);
        }

        @Override
        protected void startUp() throws Exception {
            super.startUp();

            checkCacheVersion();

            cleanupManagedTemporaryFiles();

            mCleanupNeeded.set(true);
        }

    }

    final protected Runnable mCleanupRunnable = new InterruptedAwareRunnable() {
        @Override
        protected void doRun() {
            if (!mCleanupNeeded.getAndSet(false)) {
                return;
            }

            if (isStopping()) return;

            if (OSLog.isLoggable(Tag.CACHE, OSLog.DEBUG)) {
                // periodically update the memory metrics
                String memoryMetrics = memoryMetrics();

                OSLog.d(Tag.CACHE, memoryMetrics);
            }
            OSLog.TimingLoggerCompat timing = Tag.TIMING.newTimingLogger("CacheService::cleanup");

            cleanupRenewItems();
            timing.addSplit("update last used items");

            if (isStopping()) return;
            getDatabase().cleanupPurgeServerscan();
            timing.addSplit("purge serverscan items");

            if (isStopping()) return;
            getDatabase().cleanupPurgeTimeout();
            timing.addSplit("purge timeout items");

            if (isStopping()) return;
            getDatabase().cleanupShrinkExternalCache();
            timing.addSplit("shrink external cache storage");

            if (isStopping()) return;
            getDatabase().cleanupShrinkSqliteCache();
            timing.addSplit("shrink sqlite cache storage");

            timing.close();
        }
    };
}
