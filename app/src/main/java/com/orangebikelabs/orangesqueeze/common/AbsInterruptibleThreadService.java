/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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
