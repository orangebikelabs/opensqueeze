/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.google.common.base.Objects;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.NotificationCommon;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.FileUtils;
import com.orangebikelabs.orangesqueeze.common.NavigationManager;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.compat.Compat;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.database.LookupDownloadsToStart;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class DownloadService extends Service {
    final public static int CONCURRENT_DOWNLOADS = 2;

    final public static String ACTION_STARTDOWNLOADS = "startDownloads";
    final public static String ACTION_STOPDOWNLOADS = "stopDownloads";
    final public static String PARAM_SERVERID = "serverId";

    static public Intent getStartDownloadsIntent(Context context, long serverId) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_STARTDOWNLOADS);
        intent.putExtra(PARAM_SERVERID, serverId);
        return intent;
    }

    static public Intent getStopDownloadsIntent(Context context) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_STOPDOWNLOADS);
        return intent;
    }

    protected SBContext mSbContext;
    protected NotificationManager mNotificationManager;
    protected NotificationCompat.Builder mNotificationBuilder;
    protected ListeningExecutorService mDownloadExecutor;
    protected Bitmap mLargeNotificationIcon;

    // accessed only from the main thread
    final protected Set<Long> mStartedIds = new HashSet<>();

    // accessed only from the main thread
    private long mRunningServerId;

    // accessed only from the main thread
    private int mTotalDownloads;

    // accessed only from the main thread
    private int mPendingDownloads;

    @Override
    public void onCreate() {
        super.onCreate();

        mRunningServerId = ConnectionInfo.INVALID_SERVER_ID;
        mSbContext = SBContextProvider.initializeAndGet(this);
        mStartedIds.clear();
        mPendingDownloads = 0;
        mTotalDownloads = 0;

        mLargeNotificationIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_download);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // when the download service starts, clean any cached downloads
        OSExecutors.getSingleThreadScheduledExecutor().submit(new CleanupDownloadCache(this));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mNotificationManager.cancel(Constants.NOTIFICATIONID_DOWNLOAD);

        stopDownloads();

        mLargeNotificationIcon = null;

    }

    private void stopDownloads() {
        OSAssert.assertMainThread();

        if (mDownloadExecutor != null) {
            mDownloadExecutor.shutdownNow();
            mDownloadExecutor = null;
        }
        mTotalDownloads = 0;
        mPendingDownloads = 0;

        mStartedIds.clear();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = null;
        long serverId = ConnectionInfo.INVALID_SERVER_ID;
        if (intent != null) {
            action = intent.getAction();
            serverId = intent.getLongExtra(PARAM_SERVERID, ConnectionInfo.INVALID_SERVER_ID);
        }
        if (action == null) {
            action = ACTION_STARTDOWNLOADS;
        }
        if (serverId == ConnectionInfo.INVALID_SERVER_ID) {
            serverId = SBContextProvider.get().getServerId();
        }
        if (serverId == ConnectionInfo.INVALID_SERVER_ID) {
            // still no server id?
            return Service.START_NOT_STICKY;
        }

        if (Objects.equal(action, ACTION_STARTDOWNLOADS)) {
            // check active server id....
            if (mRunningServerId != ConnectionInfo.INVALID_SERVER_ID && mRunningServerId != serverId) {
                stopDownloads();
            }
            mRunningServerId = serverId;
            if (mDownloadExecutor == null) {
                mDownloadExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(CONCURRENT_DOWNLOADS));
            }

            final ListeningExecutorService fExecutor = mDownloadExecutor;
            ListenableFuture<List<Long>> future = OSExecutors.getSingleThreadScheduledExecutor().submit(new StartAllDownloadsTask(this, serverId));
            Futures.addCallback(future, new FutureCallback<List<Long>>() {
                @Override
                public void onSuccess(@Nullable List<Long> candidateList) {
                    if (mDownloadExecutor != fExecutor || candidateList == null) {
                        // switched servers
                        return;
                    }

                    for (Long downloadId : candidateList) {
                        if (mStartedIds.add(downloadId)) {
                            addDownloadTask(downloadId, fExecutor);
                        }
                    }
                    updateNotification();
                }

                @Override
                public void onFailure(@Nullable Throwable t) {
                    Reporting.report(t);
                }
            }, OSExecutors.getMainThreadExecutor());
        } else if (Objects.equal(action, ACTION_STOPDOWNLOADS)) {
            stopDownloads();
            stopSelf();
        }

        return Service.START_STICKY;
    }

    protected void addDownloadTask(Long downloadId, final ListeningExecutorService downloadExecutor) {
        OSAssert.assertMainThread();

        mTotalDownloads++;
        mPendingDownloads++;

        ListenableFuture<?> future = downloadExecutor.submit(new DownloadTask(downloadId, SBContextProvider.get().getConnectionCredentials()));
        future.addListener(() -> {
            if (mDownloadExecutor != downloadExecutor) {
                return;
            }
            mPendingDownloads--;
            updateNotification();
        }, OSExecutors.getMainThreadExecutor());

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void updateNotification() {
        OSAssert.assertMainThread();

        NotificationCommon.INSTANCE.createNotificationChannels(mSbContext.getApplicationContext());

        if (mNotificationBuilder == null) {
            mNotificationBuilder = new NotificationCompat.Builder(this, NotificationCommon.DOWNLOAD_NOTIFICATION_CHANNEL_ID);

            Intent intent = NavigationManager.Companion.newDownloadsIntent(this);
            mNotificationBuilder.setContentIntent(PendingIntent.getActivity(this, 0, intent,
                    Compat.getDefaultPendingIntentFlags() | PendingIntent.FLAG_UPDATE_CURRENT));

            mNotificationBuilder.setContentTitle(getString(R.string.notification_downloads_title));
            mNotificationBuilder.setOngoing(true);
            mNotificationBuilder.setSmallIcon(R.drawable.ic_notification);
            mNotificationBuilder.setLargeIcon(mLargeNotificationIcon);
        }

        final int pending = mPendingDownloads;
        final int total = mTotalDownloads;

        if (pending > 0) {
            String text = getString(R.string.notification_downloads_text, total - pending, total);
            mNotificationBuilder.setContentInfo(text);
            mNotificationBuilder.setProgress(total, total - pending, false);
            mNotificationManager.notify(Constants.NOTIFICATIONID_DOWNLOAD, mNotificationBuilder.build());
        } else {
            mNotificationManager.cancel(Constants.NOTIFICATIONID_DOWNLOAD);
        }

        if (pending == 0) {
            // no pending downloads, we can stop the service
            stopSelf();
        }
    }

    /**
     * task that catalogs downloads that need to be run
     */
    static private class StartAllDownloadsTask implements Callable<List<Long>> {
        final private long mServerId;
        final private Context mContext;

        StartAllDownloadsTask(Context context, long serverId) {
            mContext = context;
            mServerId = serverId;
        }

        @Override
        public List<Long> call() {
            List<LookupDownloadsToStart> downloads = DatabaseAccess.getInstance(mContext)
                    .getDownloadQueries()
                    .lookupDownloadsToStart(mServerId)
                    .executeAsList();

            List<Long> retval = new ArrayList<>();
            for (LookupDownloadsToStart d : downloads) {
                if (d.getDownloadautostart()) {
                    retval.add(d.get_id());
                }
            }
            return retval;
        }
    }

    static private class CleanupDownloadCache implements Callable<Void> {

        final private Context mContext;

        CleanupDownloadCache(Context context) {
            mContext = context;
        }

        @Override
        public Void call() {
            // remove any temporary downloaded files
            File cacheDir = DownloadTask.getDownloadTempDirectory(mContext);
            File[] listFiles = cacheDir.listFiles();
            if (listFiles != null) {
                for (File f : listFiles) {
                    FileUtils.deleteChecked(f);
                }
            }
            return null;
        }
    }
}
