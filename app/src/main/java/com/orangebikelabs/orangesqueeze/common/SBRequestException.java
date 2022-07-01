/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.base.Function;

import java.util.concurrent.ExecutionException;

/**
 * @author tsandee
 */
public class SBRequestException extends SBException {
    final static public Function<Throwable, SBRequestException> MAPPING = e -> {
        Throwable t = e;
        if (t instanceof ExecutionException && t.getCause() != null) {
            t = e.getCause();
        }
        if (t instanceof SBRequestException || t == null) {
            return (SBRequestException) t;
        } else {
            return new SBRequestException(t.getMessage(), t);
        }
    };

    public static SBRequestException wrap(Throwable e) {
        return MAPPING.apply(e);
    }

    public SBRequestException() {
        super();
    }

    public SBRequestException(String detailMessage) {
        super(detailMessage);
    }

    public SBRequestException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public SBRequestException(Throwable throwable) {
        super(throwable);
    }
}
