/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.google.common.base.Function;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.SBException;

import java.util.concurrent.ExecutionException;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
public class SBCacheException extends SBException {
    final static public Function<Throwable, SBCacheException> MAPPING = e -> {
        Throwable t = e;
        if (t instanceof ExecutionException && t.getCause() != null) {
            t = e.getCause();
        }
        if (t instanceof SBCacheException || t == null) {
            return (SBCacheException) t;
        } else {
            return new SBCacheException(t.getMessage(), t);
        }
    };

    @Nonnull
    public static SBCacheException wrap(Throwable e) {
        SBCacheException retval = MAPPING.apply(e);
        OSAssert.assertNotNull(retval, "should never return null");
        return retval;
    }

    public SBCacheException() {
    }

    public SBCacheException(String detailMessage) {
        super(detailMessage);
    }

    public SBCacheException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public SBCacheException(Throwable throwable) {
        super(throwable);
    }
}
