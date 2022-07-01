/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.collect.MapMaker;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;

/**
 * List Job Processor is a specialized job queue for use with ListView objects where scrolling through the list can cause jobs to be
 * redundant/useless very quickly.
 * <p/>
 * <ol>
 * Parameterized for three types.
 * <li>the entity type. Often, but not always, this is an ImageView that we are trying to populate in the background.
 * <li>the job, which simply defines what task needs to be done (album art id, for example)
 * <li>the result of the task
 * </ol>
 * <p/>
 * Subclass implements two methods. One method to perform the task and another to commit the task's results, which may not be called if the
 * list item has scrolled out of view already and been recycled.
 *
 * @author tbsandee@orangebikelabs.com
 */

public class ListJobProcessor<E> {
    final static private long TASK_KEEPALIVE = 10000;
    final static private TimeUnit TASK_KEEPALIVE_UNITS = TimeUnit.MILLISECONDS;

    final static public int SECONDARY_JOB_LIFESPAN = 20;

    final static protected AtomicInteger sSecondaryJobId = new AtomicInteger(Integer.MIN_VALUE);
    final static protected AtomicInteger sPrimaryJobId = new AtomicInteger(0);

    @Nonnull
    final static protected ThreadPoolExecutor sExecutor;

    static {
        final int taskCount = 4 + Runtime.getRuntime().availableProcessors();
        sExecutor = new OSExecutors.SafeThreadPoolExecutor(taskCount, taskCount, TASK_KEEPALIVE, TASK_KEEPALIVE_UNITS,
                new PriorityBlockingQueue<>(), Thread.NORM_PRIORITY - 1, "ListJobProcessor # %1$d");
    }

    @Nonnull
    final protected ConcurrentMap<E, JobTask<E>> mJobMap = new MapMaker().concurrencyLevel(2).weakKeys().makeMap();

    final private AtomicBoolean mStarted = new AtomicBoolean(false);

    final private AtomicBoolean mPaused = new AtomicBoolean(false);

    public interface Job {

        /**
         * returns whether or not the job needs to be committed
         */
        boolean execute();

        /**
         * commit the job
         */
        void commit();

        /**
         * abort the job, release resources etc
         */
        void abort();
    }

    public ListJobProcessor() {
    }

    public void addSecondaryJob(Job job) {
        JobTask<E> task = newJobTask(null, job, sSecondaryJobId.incrementAndGet());
        sExecutor.execute(task);
    }

    public void addJob(E entity, Job job) {
        JobTask<E> task = newJobTask(entity, job, sPrimaryJobId.incrementAndGet());
        JobTask<E> oldTask;

        // synchronized to avoid possible race condition
        synchronized (this) {
            oldTask = mJobMap.put(entity, task);
            sExecutor.execute(task);
        }
        if (oldTask != null) {
            oldTask.cancel(false);
        }
    }

    public void onStart() {
        OSAssert.assertMainThread();

        setStarted(true);
    }

    public void onStop() {
        OSAssert.assertMainThread();
        setStarted(false);
    }

    private void setStarted(boolean started) {
        boolean running = isRunning();
        mStarted.set(started);
        transitionToState(running);
    }

    public boolean setPaused(boolean paused) {
        mPaused.set(paused);
        return isRunning();
    }

    public boolean isRunning() {
        return !mPaused.get() && mStarted.get();
    }

    protected void onRunning() {
        // anything?
    }

    protected void onNotRunning() {
        // anything?
    }

    private void transitionToState(boolean oldRunning) {
        boolean newRunning = isRunning();
        if (oldRunning && !newRunning) {
            // iterate through, canceling and removing each task
            Iterator<Map.Entry<E, JobTask<E>>> it = mJobMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<E, JobTask<E>> entry = it.next();
                it.remove();

                entry.getValue().cancel(false);
            }
            onNotRunning();
        } else if (!oldRunning && newRunning) {
            // do start
            onRunning();
        }
    }

    /**
     * Remove any job queues tagged for the specific key.
     */
    @OverridingMethodsMustInvokeSuper
    public void removeJob(E jobEntity) {
        JobTask<E> t = mJobMap.remove(jobEntity);
        if (t != null) {
            t.cancel(false);
        }
    }

    private JobTask<E> newJobTask(@Nullable E entity, Job job, int order) {
        MyCallable<E> r = new MyCallable<>(entity, job, order);
        return new JobTask<>(mJobMap, r);
    }

    static final int STATE_WAITING = 0;
    static final int STATE_CANCELLED = 1;
    static final int STATE_RUNNING = 2;

    /**
     * spins through any pending coverart lookup and populates the pending drawable sets queue
     */
    static private class MyCallable<E> implements Callable<Void> {
        @Nullable
        final private WeakReference<E> mEntityRef;

        @Nonnull
        final private Job mJob;

        final int mOrder;

        @Nonnull
        final private AtomicInteger mState = new AtomicInteger(STATE_WAITING);

        volatile private JobTask<E> mTask;

        MyCallable(@Nullable E entity, Job job, int order) {
            if (entity != null) {
                mEntityRef = new WeakReference<>(entity);
            } else {
                mEntityRef = null;
            }
            mJob = job;
            mOrder = order;
        }

        @Nullable
        E getEntity() {
            E retval = null;
            if (mEntityRef != null) {
                retval = mEntityRef.get();
            }
            return retval;
        }

        void init(JobTask<E> task) {
            mTask = task;
        }

        boolean cancel() {
            boolean retval = mState.compareAndSet(STATE_WAITING, STATE_CANCELLED);
            if (retval) {
                // successfully transitioned to cancelled state, abort the job
                mJob.abort();
            }
            return retval;
        }

        @Override
        public Void call() {
            if (!mState.compareAndSet(STATE_WAITING, STATE_RUNNING)) {
                // was already cancelled, just return
                return null;
            }

            boolean abort = false;
            boolean commit = false;
            E e = getEntity();
            if (e == null && (mOrder + SECONDARY_JOB_LIFESPAN) < sSecondaryJobId.get()) {
                // if the job is older than the specified secondary item lifespan, skip it
                abort = true;
            } else {
                commit = mJob.execute();
                if (e != null) {
                    boolean wasCurrent = mTask.mJobMap.remove(e, mTask);
                    if (!wasCurrent) {
                        // job was cancelled because entity/job pairing is changed or otherwise removed
                        abort = true;
                    }
                }
            }
            if (!abort && mTask.isCancelled()) {
                abort = true;
                if (e != null) {
                    mTask.mJobMap.remove(e, mTask);
                }
            }

            if (abort) {
                mJob.abort();
            } else if (commit) {
                mJob.commit();
            }

            return null;
        }
    }

    static class JobTask<E> extends FutureTask<Void> implements Comparable<JobTask<E>> {
        final private int mOrder;

        @Nonnull
        final ConcurrentMap<E, ?> mJobMap;

        @Nonnull
        final MyCallable<E> mCallable;

        public JobTask(ConcurrentMap<E, ?> jobMap, MyCallable<E> callable) {
            super(callable);

            mCallable = callable;
            mJobMap = jobMap;
            mOrder = callable.mOrder;

            callable.init(this);
        }

        @Override
        public int compareTo(JobTask<E> another) {
            return Integer.compare(mOrder, another.mOrder);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            // let callable run through, cancel it there
            if (mCallable.cancel()) {
                E entity = mCallable.getEntity();
                if (entity != null) {
                    mJobMap.remove(entity, this);
                }
                return true;
            }

            // it's already running, send abort to thread
            return super.cancel(mayInterruptIfRunning);
        }

        /**
         * use identity hash code
         */
        @Override
        public int hashCode() {
            return super.hashCode();
        }

        /**
         * use identity equals
         */
        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }
    }
}
