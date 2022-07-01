/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.content.Context;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.players.SqueezePlayerHelper;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class SqueezePlayerPingService extends AbstractScheduledService {
    final private SBContext mSbContext;
    final private Context mApplicationContext;

    public SqueezePlayerPingService() {
        mSbContext = SBContextProvider.get();
        mApplicationContext = mSbContext.getApplicationContext();
    }

    @Override
    protected ScheduledExecutorService executor() {
        return OSExecutors.getSingleThreadScheduledExecutor();
    }

    @Override
    protected void runOneIteration() {
        if (mSbContext.isConnected() && SqueezePlayerHelper.isAvailable(mApplicationContext)) {
            for (PlayerStatus s : mSbContext.getServerStatus().getAvailablePlayers()) {
                if (s.isLocalSqueezePlayer()) {
                    OSExecutors.getMainThreadExecutor().execute(() -> SqueezePlayerHelper.pingService(mApplicationContext));
                }
            }
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, Constants.SQUEEZEPLAYER_PING_INTERVAL, Constants.TIME_UNITS);
    }
}
