/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
