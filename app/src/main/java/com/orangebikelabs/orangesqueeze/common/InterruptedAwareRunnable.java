/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

/**
 * @author tsandee
 */
abstract public class InterruptedAwareRunnable implements Runnable {
    @Override
    public void run() {
        try {
            doRun();
        } catch (InterruptedException e) {
            // ignore, expected
        }
    }

    protected abstract void doRun() throws InterruptedException;
}
