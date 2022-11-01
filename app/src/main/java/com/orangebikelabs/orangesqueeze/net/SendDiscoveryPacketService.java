/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

public class SendDiscoveryPacketService extends AbstractScheduledService {
    private long mInterval;
    private TimeUnit mUnits;

    public SendDiscoveryPacketService(long interval, TimeUnit units) {
        mUnits = units;
        mInterval = interval;
    }

    @Override
    @Nonnull
    protected ScheduledExecutorService executor() {
        return OSExecutors.getSingleThreadScheduledExecutor();
    }

    @Override
    protected void runOneIteration() throws Exception {
        DiscoveryService.triggerDiscovery(SBContextProvider.get().getApplicationContext());
    }

    @Override
    @Nonnull
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, mInterval, mUnits);
    }
}