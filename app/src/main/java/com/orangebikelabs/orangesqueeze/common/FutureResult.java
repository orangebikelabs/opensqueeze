/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
public class FutureResult extends ForwardingListenableFuture.SimpleForwardingListenableFuture<SBResult> {

    @Nonnull
    public static FutureResult result(ListenableFuture<SBResult> future) {
        return new FutureResult(future);
    }

    // enforce timeouts for checkedGet()
    @Nonnull
    public SBResult checkedGet() throws SBRequestException, InterruptedException {
        try {
            SBResult result = super.get(Constants.READ_TIMEOUT, Constants.TIME_UNITS);
            OSAssert.assertNotNull(result, "result should never be null");
            return result;
        } catch (TimeoutException e) {
            throw new SBRequestException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            Throwables.propagateIfPossible(cause, SBRequestException.class, InterruptedException.class);
            throw SBRequestException.wrap(cause);
        }
    }

    // enforce timeouts for get()
    @Override
    @Nonnull
    public SBResult get() throws InterruptedException, ExecutionException {
        try {
            SBResult result = super.get(Constants.READ_TIMEOUT, Constants.TIME_UNITS);
            OSAssert.assertNotNull(result, "result should never be null");
            return result;
        } catch (TimeoutException e) {
            throw new ExecutionException(e);
        }
    }

    @Nonnull
    public static FutureResult immediateFailedResult(SBRequestException e) {
        ListenableFuture<SBResult> future = Futures.immediateFailedFuture(e);
        return new FutureResult(future);
    }

    @Nonnull
    public static FutureResult immediateResult(SBResult result) {
        ListenableFuture<SBResult> checked = Futures.immediateFuture(result);
        return new FutureResult(checked);
    }

    private FutureResult(ListenableFuture<SBResult> future) {
        super(future);
    }

    public boolean isCommitted() {
        if (!isDone() || isCancelled()) {
            return false;
        }

        try {
            SBResult result = checkedGet();
            return result.isCommitted();
        } catch (SBRequestException | InterruptedException e) {
            return false;
        }
    }
}
