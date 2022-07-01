/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.os.Handler;
import android.os.Looper;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * A utility thread pool that runs at slightly higher-than-minimum priority.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class OSExecutors {
    final static String SINGLE_SCHEDULED_THREAD_NAME = "OS Scheduling Thread";

    final static private long TASK_KEEPALIVE = 60;
    final static private TimeUnit TASK_KEEPALIVE_UNITS = TimeUnit.SECONDS;
    final static private int CORE_TASK_COUNT = 4;

    final static protected Handler sHandler = new Handler(Looper.getMainLooper());

    final static private ListeningExecutorService sUnboundedPool;
    final static private ListeningExecutorService sCommandExecutor;
    final static private ListeningScheduledExecutorService sScheduledPool;

    final static private ListeningExecutorService sMainThreadExecutor;

    static {
        ExecutorService unboundedTemp = new SafeThreadPoolExecutor(CORE_TASK_COUNT, Integer.MAX_VALUE, TASK_KEEPALIVE, TASK_KEEPALIVE_UNITS,
                new SynchronousQueue<>(), Thread.NORM_PRIORITY - 1, "OS pool # %1$d");
        sUnboundedPool = MoreExecutors.listeningDecorator(unboundedTemp);

        sCommandExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(new MyThreadFactory(Thread.NORM_PRIORITY, "OS Command Thread")));

        sScheduledPool = newSingleThreadScheduledExecutor(SINGLE_SCHEDULED_THREAD_NAME);

        sMainThreadExecutor = MoreExecutors.listeningDecorator(new AbstractExecutorService() {
            final private CountDownLatch mShutdownLatch = new CountDownLatch(1);

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return mShutdownLatch.await(timeout, unit);
            }

            @Override
            public boolean isShutdown() {
                return mShutdownLatch.getCount() == 0;
            }

            @Override
            public boolean isTerminated() {
                return isShutdown();
            }

            @Override
            public void shutdown() {
                mShutdownLatch.countDown();
            }

            @Override
            @Nonnull
            public List<Runnable> shutdownNow() {
                mShutdownLatch.countDown();
                return Collections.emptyList();
            }

            @Override
            public void execute(Runnable command) {
                sHandler.post(command);
            }
        });
    }

    /**
     * create a new single-threaded executor
     */
    @Nonnull
    public static ListeningScheduledExecutorService newSingleThreadScheduledExecutor(String name) {

        // make sure this uses a slightly reduced-priority thread
        ScheduledExecutorService retval = new ScheduledThreadPoolExecutor(1, new MyThreadFactory(Thread.NORM_PRIORITY - 1, name)) {

            @Override
            protected void afterExecute(Runnable r, @Nullable Throwable t) {
                super.afterExecute(r, t);

                // reset priority
                MyThreadFactory factory = (MyThreadFactory) getThreadFactory();
                Thread.currentThread().setPriority(factory.mDefaultPriority);

                // handle exceptions
                if (t == null && r instanceof Future<?>) {
                    try {
                        Future<?> future = (Future<?>) r;
                        if (future.isDone()) {
                            future.get();
                        }
                    } catch (CancellationException ce) {
                        // ignore, we expect to get this sometimes
                    } catch (ExecutionException ee) {
                        t = ee.getCause();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); // ignore/reset
                    }
                }
                if (t != null) {
                    if (t instanceof RuntimeException || t instanceof Error) {
                        OSLog.e(t.getMessage(), t);
                    } else {
                        OSLog.i("Exception", t);
                    }
                }
            }
        };
        return MoreExecutors.listeningDecorator(retval);
    }

    @Nonnull
    public static ListeningExecutorService getMainThreadExecutor() {
        return sMainThreadExecutor;
    }

    @Nonnull
    public static ListeningExecutorService getUnboundedPool() {
        return sUnboundedPool;
    }

    @Nonnull
    public static ListeningExecutorService getCommandExecutor() {
        return sCommandExecutor;
    }

    @Nonnull
    public static ListeningScheduledExecutorService getSingleThreadScheduledExecutor() {
        return sScheduledPool;
    }

    @Nonnull
    public static Scheduler singleThreadScheduler() {
        return Schedulers.from(sScheduledPool);
    }

    static public class SafeThreadPoolExecutor extends ThreadPoolExecutor {
        public SafeThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                      int defaultPriority, String threadNameFormat) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new MyThreadFactory(defaultPriority, threadNameFormat), new AbortPolicy());
        }

        @Override
        protected void afterExecute(Runnable r, @Nullable Throwable t) {
            super.afterExecute(r, t);

            // reset priority
            MyThreadFactory factory = (MyThreadFactory) getThreadFactory();
            Thread.currentThread().setPriority(factory.mDefaultPriority);

            // handle exceptions
            if (t == null && r instanceof Future<?>) {
                try {
                    Future<?> future = (Future<?>) r;
                    if (future.isDone()) {
                        future.get();
                    }
                } catch (CancellationException ce) {
                    // ignore, we expect to get this sometimes
                } catch (ExecutionException ee) {
                    t = ee.getCause();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // ignore/reset
                }
            }
            if (t != null) {
                if (t instanceof RuntimeException || t instanceof Error) {
                    OSLog.e(t.getMessage(), t);
                } else {
                    OSLog.i("Exception", t);
                }
            }
        }
    }

    static private class MyThreadFactory implements ThreadFactory {
        final private AtomicInteger mThreadCount = new AtomicInteger();
        final private String mThreadNameFormat;
        final protected int mDefaultPriority;

        MyThreadFactory(int priority, String threadNameFormat) {
            mDefaultPriority = priority;
            mThreadNameFormat = threadNameFormat;
        }

        @Override
        @Nonnull
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setPriority(mDefaultPriority);
            String threadName = String.format(mThreadNameFormat, mThreadCount.getAndIncrement());
            t.setName(threadName);
            return t;
        }
    }
}
