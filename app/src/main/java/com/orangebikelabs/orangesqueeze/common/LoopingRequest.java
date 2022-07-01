/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.content.Context;
import android.database.ContentObservable;
import android.database.ContentObserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.SBRequest.Type;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Request designed to loop through retrieval of a large series of items in batches, like artist list, album lists, etc.
 * <p/>
 * All methods are threadsafe and can be run in the background using the submit() method.
 *
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
public class LoopingRequest {

    public static final int NORMAL_BATCH_COUNT = 250;
    public static final int INITIAL_BATCH_COUNT = 100;

    final private ContentObservable mObservable = new ContentObservable();

    @Nonnull
    final protected Context mContext;

    @Nonnull
    final protected SBContext mSbContext;

    final private List<String> mParameters = Lists.newCopyOnWriteArrayList();
    final private List<String> mCommands = Lists.newCopyOnWriteArrayList();
    final private List<String> mLoopKeys = Lists.newCopyOnWriteArrayList(Arrays.asList("item_loop", "loop_loop"));
    final private List<String> mCountKeys = Lists.newCopyOnWriteArrayList(Arrays.asList("count", "count"));

    /**
     * updated regularly during looping, in theory this is marginally faster due to reduced synchronization
     */
    final private AtomicInteger mPosition = new AtomicInteger(0);

    /**
     * used regularly from several threads, reduce lock contention
     */
    final protected AtomicBoolean mIsStarted = new AtomicBoolean(false);

    /**
     * used outside of normal sync blocks, simpler to use atomic
     */
    final protected AtomicBoolean mAborted = new AtomicBoolean();

    @GuardedBy("this")
    private int mTotalRecordCount;

    @GuardedBy("this")
    private int mBatchSize = INITIAL_BATCH_COUNT;

    @GuardedBy("this")
    private int mMaxRows = Integer.MAX_VALUE;

    @GuardedBy("this")
    @Nullable
    private FutureResult mLastResult;

    @GuardedBy("this")
    @Nullable
    private ListenableFuture<Void> mTask;

    @GuardedBy("this")
    private boolean mIsFirstLoop;

    @GuardedBy("this")
    private boolean mCacheable = false;

    @GuardedBy("this")
    private boolean mRefreshCache = false;

    /**
     * private so that subclasses can override the call to getPlayerId() to determine effective player ID at runtime
     */
    @Nullable
    final private PlayerId mPlayerId;

    /**
     * default constructor
     */
    public LoopingRequest(@Nullable PlayerId playerId) {
        mSbContext = SBContextProvider.get();
        mContext = mSbContext.getApplicationContext();
        mPlayerId = playerId;

        internalResetRequestData();
    }

    public void addParameter(String paramString) {
        mParameters.add(paramString);
    }

    public void addParameter(String param, String value) {
        mParameters.add(param + ":" + value);
    }

    @Nonnull
    public List<String> getCommands() {
        return Collections.unmodifiableList(mCommands);
    }

    /**
     * resets request, does NOT notify observers
     */
    synchronized public void reset() {
        stop();

        internalResetRequestData();
    }

    synchronized private void internalResetRequestData() {
        mLastResult = null;
        mTotalRecordCount = 0;
        mPosition.set(0);
        mIsFirstLoop = true;
        mAborted.set(false);
    }

    synchronized public LoopingRequestData newLoopingRequestData() {
        return new LoopingRequestData(this);
    }

    public boolean isStarted() {
        return mIsStarted.get();
    }

    synchronized public boolean isCacheable() {
        return mCacheable;
    }

    synchronized public void setCacheable(boolean cacheable) {
        mCacheable = cacheable;
    }

    synchronized public boolean shouldRefreshCache() {
        return mRefreshCache;
    }

    synchronized public void setShouldRefreshCache(boolean refresh) {
        mRefreshCache = refresh;
    }

    @Nonnull
    synchronized public ListenableFuture<Void> submit( ListeningExecutorService executor) {
        if (isComplete()) {
            // request is already completed, no need to reissue it
            return Futures.immediateFuture(null);
        }

        if (mIsStarted.get() && mTask != null) {
            return mTask;
        }

        // is task still running (not a clean shutdown using stop())
        if (mTask != null && !mTask.isDone()) {
            mTask = null;
            // force a reissue of the request
            internalResetRequestData();
        }

        mIsStarted.set(true);
        mTask = executor.submit(mCallable);
        return mTask;
    }

    synchronized public void stop() {
        if (!mIsStarted.get()) {
            return;
        }
        mIsStarted.set(false);

        if (mTask != null) {
            mTask.cancel(false);
        }
    }

    final private Callable<Void> mCallable = () -> {
        // looping requests have the highest priority, they are blocking the UI
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        mIsStarted.set(true);
        boolean success = false;
        try {
            try {
                initializeRequest();
                performRequest();
                success = true;
            } catch (InterruptedException e) {
                // ignore
            } catch (Exception e) {
                OSLog.w("Exception executing request " + toString(), e);
            }
        } finally {
            mIsStarted.set(false);
            if (success) {
                try {
                    finalizeRequest();
                } catch (Exception e) {
                    OSLog.w(e.getMessage(), e);
                }
            } else {
                mAborted.set(true);
                try {
                    abortRequest();
                } catch (Exception e) {
                    OSLog.w(e.getMessage(), e);
                }
            }
        }
        return null;
    };

    protected void initializeRequest() throws SBRequestException {
        // no implementation by default
    }

    protected void finalizeRequest() throws SBRequestException {
        // no implementation by default
    }

    protected void abortRequest() throws SBRequestException {
        // no implementation by default
    }

    /**
     * subclasses override this
     */
    protected void performRequest() throws SBRequestException, InterruptedException {
        if (mLoopKeys.size() != mCountKeys.size()) {
            throw new IllegalArgumentException("loop key count must match count key count");
        }

        final int maxRows = getMaxRows();

        while (!checkDone()) {
            final int startingPosition = mPosition.get();
            final int loopSize = getCurrentLoopRequestSize();

            List<Object> commands = prepareCommands();
            SBRequest lastRequest = mSbContext.newRequest(Type.COMET, commands);
            lastRequest.setPlayerId(getPlayerId());
            lastRequest.setCacheable(isCacheable());
            lastRequest.setShouldRefreshCache(shouldRefreshCache());

            FutureResult futureResult = lastRequest.submit(MoreExecutors.newDirectExecutorService());
            setLastResult(futureResult);
            SBResult result = futureResult.checkedGet();

            // if request threw an exception OR started flag was changed while we were issuing the request, bail right now
            if (!mIsStarted.get()) {
                continue;
            }

            boolean countKeysFound = false;

            JsonNode arrayNode = null;

            JsonNode json = result.getJsonResult();
            if (isFirstLoop()) {
                int calculateMaximumRecordCount = 0;
                final int countSize = mCountKeys.size();
                for (int i = 0; i < countSize; i++) {
                    String countKey = mCountKeys.get(i);

                    JsonNode countNode = json.get(countKey);
                    if (countNode != null) {
                        countKeysFound = true;
                        String loopKey = mLoopKeys.get(i);
                        if (json.has(loopKey)) {
                            OSLog.d(Tag.DEFAULT, "Found loop key " + loopKey + " in JSON response", json);
                            calculateMaximumRecordCount += countNode.asInt();
                        } else {
                            OSLog.d(Tag.DEFAULT, "Ignoring missing loop key " + loopKey + " in JSON response", json);
                        }
                    }
                }
                if (!countKeysFound) {
                    // try another technique
                    JsonNode secondaryData = json.get("data");
                    if (secondaryData != null && secondaryData.isArray()) {
                        arrayNode = secondaryData;
                        calculateMaximumRecordCount = arrayNode.size();
                    }
                }
                setTotalRecordCount(calculateMaximumRecordCount);
                markFirstLoopComplete();
            }
            // called for symmetry to onFinishLoop()
            onStartLoop(result);

            int thisLoopRawSize = 0;
            if (arrayNode != null) {
                thisLoopRawSize = arrayNode.size();
                for (int i = 0; i < thisLoopRawSize; i++) {
                    JsonNode node = arrayNode.get(i);

                    if (!node.isObject()) {
                        Reporting.report(null, "Unexpected non-object node", node);
                        continue;
                    }
                    if (node.size() == 0) {
                        // skipping empty node (comment fields, for now)
                        continue;
                    }
                    onLoopItem(result, (ObjectNode) node);

                    if (mPosition.incrementAndGet() >= maxRows) {
                        break;
                    }
                }
            } else {
                // FIXME sometimes empty lists are returned with count=1, increment things properly to avoid infinite loops
                // this seems to happen during a scan, investigate this because it causes infinite reloads on loopingtaskloader
                final int loopKeysSize = mLoopKeys.size();

                outer:
                for (int i = 0; i < loopKeysSize; i++) {
                    String loopKey = mLoopKeys.get(i);

                    JsonNode loop = json.get(loopKey);
                    if (loop == null) {
                        continue;
                    }

                    final int size = loop.size();
                    thisLoopRawSize += size;

                    for (int j = 0; j < size; j++) {
                        JsonNode node = loop.get(j);

                        if (!node.isObject()) {
                            Reporting.report(null, "Unexpected non-object node", node);
                            continue;
                        }
                        if (node.size() == 0) {
                            // skipping empty node (comment fields, for now)
                            continue;
                        }
                        onLoopItem(result, (ObjectNode) node);

                        if (mPosition.incrementAndGet() >= maxRows) {
                            break outer;
                        }
                    }
                }
            }
            // add a hook for the entire result, called after the responsehandler
            onFinishLoop(result);

            if (thisLoopRawSize < loopSize) {
                // truncated results, end it
                setTotalRecordCount(mPosition.get());
            } else if (mPosition.get() == startingPosition) {
                // request didn't return any valid results don't try again
                setTotalRecordCount(mPosition.get());
            }
            // at the end of each loop, notify observers
            notifyObservers();
        }
    }

    @Nullable
    public synchronized FutureResult getLastResult() {
        return mLastResult;
    }

    @Nullable
    public PlayerId getPlayerId() {
        return mPlayerId;
    }

    public int getPosition() {
        return mPosition.get();
    }

    synchronized public int getTotalRecordCount() {
        return mTotalRecordCount;
    }

    synchronized protected boolean isFirstLoop() {
        return mIsFirstLoop;
    }

    public boolean isAborted() {
        return mAborted.get();
    }

    synchronized protected void markFirstLoopComplete() {
        mIsFirstLoop = false;
        mBatchSize = NORMAL_BATCH_COUNT;
    }

    synchronized public void setMaxRows(int maxRows) {
        mMaxRows = maxRows;
    }

    synchronized public int getMaxRows() {
        return mMaxRows;
    }

    synchronized public boolean isComplete() {
        if (mAborted.get()) {
            // some sort of error occurred, don't try to resume this
            return true;
        }

        if (mIsFirstLoop) {
            // otherwise, always execute at least one loop
            return false;
        }

        if (mPosition.get() >= mTotalRecordCount) {
            // reached eof
            return true;
        }

        if (mPosition.get() >= mMaxRows) {
            // reached max rows
            return true;
        }

        // not done
        return false;
    }

    synchronized private boolean checkDone() {
        if (!mIsStarted.get()) {
            // request is stopped, don't continue
            return true;
        }

        return isComplete();
    }

    protected void notifyObservers() {
        mObservable.dispatchChange(true, null);
    }

    protected void onFinishLoop(SBResult loopingResult) throws SBRequestException {
        // no implementation by default
    }

    protected void onLoopItem(SBResult loopingResult, ObjectNode item) throws SBRequestException {
        // no implementation by default
    }

    protected void onStartLoop(SBResult loopingResult) throws SBRequestException {
        // no implementation by default
    }

    public boolean paramMatches(String param) {
        return mParameters.contains(param);
    }

    @Nonnull
    synchronized protected List<Object> prepareCommands() {
        final int MIDDLE_SIZE = 2;
        Object[] retval = new Object[mCommands.size() + MIDDLE_SIZE + mParameters.size()];

        int ndx = 0;

        final int commandSize = mCommands.size();
        for (int i = 0; i < commandSize; i++) {
            retval[ndx++] = mCommands.get(i);
        }

        // MIDDLE_SIZE
        retval[ndx++] = Integer.toString(mPosition.get());
        retval[ndx++] = Integer.toString(getCurrentLoopRequestSize());

        final int parameterSize = mParameters.size();
        for (int i = 0; i < parameterSize; i++) {
            retval[ndx++] = mParameters.get(i);
        }

        return Arrays.asList(retval);
    }

    synchronized protected int getCurrentLoopRequestSize() {
        return Math.min(mBatchSize, mMaxRows);
    }

    public void registerObserver(ContentObserver ob) {
        mObservable.registerObserver(ob);
    }

    public void removeParameter(String param) {
        final int paramSize = mParameters.size();
        for (int i = 0; i < paramSize; i++) {
            if (mParameters.get(i).startsWith(param + ":")) {
                mParameters.remove(i);
                break;
            }
        }

    }

    public void setCommands(List<String> commands) {
        mCommands.clear();
        mCommands.addAll(commands);
    }

    public void setCommands(@Nullable String... commands) {
        if (commands == null) {
            commands = new String[0];
        }
        setCommands(Arrays.asList(commands));
    }

    public void setLoopAndCountKeys(List<String> loopKeys, @Nullable List<String> countKeys) {
        if (countKeys == null) {
            countKeys = Collections.nCopies(loopKeys.size(), "count");
        }
        if (loopKeys.size() != countKeys.size()) {
            throw new IllegalArgumentException();
        }

        mLoopKeys.clear();
        mLoopKeys.addAll(loopKeys);

        mCountKeys.clear();
        mCountKeys.addAll(countKeys);
    }

    synchronized public void setInitialBatchSize(int batchSize) {
        if (batchSize <= 0) {
            mBatchSize = 10000000;
        } else {
            mBatchSize = batchSize;
        }
    }

    protected synchronized void setLastResult(FutureResult result) {
        mLastResult = result;
    }

    public void setParameter(String param, String value) {
        boolean found = false;

        String newParamValue = param + ":" + value;
        for (int i = 0; i < mParameters.size(); i++) {
            if (mParameters.get(i).startsWith(param + ":")) {
                mParameters.set(i, newParamValue);
                found = true;
                break;
            }
        }

        if (!found) {
            mParameters.add(newParamValue);
        }
    }

    public void setParameters(List<String> params) {
        mParameters.clear();
        mParameters.addAll(params);
    }

    public void setParameters(@Nullable String... parameters) {
        if (parameters == null) {
            parameters = new String[0];
        }
        setParameters(Arrays.asList(parameters));
    }

    synchronized private void setTotalRecordCount(int totalRecordCount) {
        mTotalRecordCount = totalRecordCount;
    }

    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("commands", mCommands)
                .add("params", mParameters)
                .add("playerId", mPlayerId)
                .toString();
    }
}
