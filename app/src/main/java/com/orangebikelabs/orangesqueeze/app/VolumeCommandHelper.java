/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.os.SystemClock;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.InterruptedAwareRunnable;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class VolumeCommandHelper {

    /**
     * static cache of volume command helpers, one for each player
     */
    final static private ConcurrentMap<PlayerId, VolumeCommandHelper> sCache = new ConcurrentHashMap<>();

    public static VolumeCommandHelper getInstance(PlayerId playerId) {
        VolumeCommandHelper retval = sCache.get(playerId);
        if (retval == null) {
            VolumeCommandHelper newVal = new VolumeCommandHelper(playerId);
            VolumeCommandHelper existingVal = sCache.putIfAbsent(playerId, newVal);
            if (existingVal == null) {
                retval = newVal;
            } else {
                retval = existingVal;
            }
        }
        return retval;
    }

    final protected static int VOLUME_ABSOLUTE_DIFF = 1000;
    final protected PlayerId mPlayerId;

    @GuardedBy("this")
    protected int mPlayerVolume = Integer.MIN_VALUE;

    @GuardedBy("this")
    final protected List<SettableFuture<SBResult>> mResultList = new ArrayList<>();

    @GuardedBy("this")
    protected Runnable mRunnable;

    @GuardedBy("this")
    protected long mRunNotBefore = 0L;

    protected VolumeCommandHelper(PlayerId playerId) {
        mPlayerId = playerId;
    }

    synchronized public FutureResult setPlayerVolume(int volumeAsInt) {
        mPlayerVolume = volumeAsInt + VOLUME_ABSOLUTE_DIFF;
        return sendVolumeRequest();
    }

    synchronized public int getPlayerVolume() {
        return mPlayerVolume;
    }

    synchronized public FutureResult incrementPlayerVolume(int volumeDiff) {
        if (mPlayerVolume == Integer.MIN_VALUE) {
            mPlayerVolume = volumeDiff;
        } else {
            mPlayerVolume += volumeDiff;
        }
        return sendVolumeRequest();
    }

    synchronized protected FutureResult sendVolumeRequest() {
        // create a settable future to return back to client, will be completed when query is actually executed
        SettableFuture<SBResult> future = SettableFuture.create();

        mResultList.add(future);

        if (mRunnable == null) {
            // if the last setvolume query was executed, send a new one
            mRunnable = new SetVolumeRunnable();
            OSExecutors.getUnboundedPool().submit(mRunnable);
        }
        return FutureResult.result(future);
    }

    synchronized protected long getRunNotBefore() {
        return mRunNotBefore;
    }

    class SetVolumeRunnable extends InterruptedAwareRunnable {
        @Override
        protected void doRun() throws InterruptedException {
            waitForStartTime();

            List<SettableFuture<SBResult>> settableFutureList;
            int setVolume;
            synchronized (VolumeCommandHelper.this) {
                mRunnable = null;
                mRunNotBefore = SystemClock.uptimeMillis() + 500;
                setVolume = mPlayerVolume;
                mPlayerVolume = Integer.MIN_VALUE;
                settableFutureList = new ArrayList<>(mResultList);
                mResultList.clear();
            }

            if (setVolume != Integer.MIN_VALUE) {
                String volumeStr;
                if (setVolume > 100) {
                    volumeStr = Integer.toString(setVolume - VOLUME_ABSOLUTE_DIFF);
                } else {
                    if (setVolume >= 0) {
                        volumeStr = "+" + setVolume;
                    } else {
                        volumeStr = Integer.toString(setVolume);
                    }
                }
                FutureResult result = SBContextProvider.get().sendPlayerCommand(mPlayerId, "mixer", "volume", volumeStr);

                final List<SettableFuture<SBResult>> futureList = settableFutureList;
                Futures.addCallback(result, new FutureCallback<SBResult>() {
                    @Override
                    public void onFailure(Throwable e) {
                        for (SettableFuture<SBResult> future : futureList) {
                            future.setException(e);
                        }
                    }

                    @Override
                    public void onSuccess(@Nullable SBResult result) {
                        for (SettableFuture<SBResult> future : futureList) {
                            future.set(result);
                        }
                    }
                }, MoreExecutors.directExecutor());
            }
        }

        private void waitForStartTime() throws InterruptedException {
            long waitForTime = getRunNotBefore();
            if (waitForTime != 0) {
                long diff;
                while ((diff = waitForTime - SystemClock.uptimeMillis()) > 0) {
                    Thread.sleep(diff);
                }
            }
        }
    }
}
