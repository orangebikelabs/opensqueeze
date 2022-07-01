/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

import javax.annotation.OverridingMethodsMustInvokeSuper;

/**
 * @author tsandee
 */
abstract public class AbsInterruptibleThreadService extends AbstractExecutionThreadService {

    private volatile Thread mThread;

    @OverridingMethodsMustInvokeSuper
    @Override
    protected void startUp() throws Exception {
        mThread = Thread.currentThread();
    }

    @OverridingMethodsMustInvokeSuper
    @Override
    protected void triggerShutdown() {
        interruptService();
    }

    public void interruptService() {
        Thread interruptThread = mThread;
        if (interruptThread != null) {
            interruptThread.interrupt();
        }
    }
}
