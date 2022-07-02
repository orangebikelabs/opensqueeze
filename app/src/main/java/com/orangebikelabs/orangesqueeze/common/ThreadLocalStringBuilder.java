/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import javax.annotation.Nonnull;

/**
 * Utility class for cases where we want to use a StringBuilder for each ThreadLocal to reduce GC overhead.
 */
public class ThreadLocalStringBuilder {

    @Nonnull
    static public ThreadLocalStringBuilder newInstance() {
        return new ThreadLocalStringBuilder();
    }

    final private InternalThreadLocal mStringBuilder = new InternalThreadLocal();

    private ThreadLocalStringBuilder() {
    }

    @Nonnull
    public StringBuilder get() {
        StringBuilder retval = mStringBuilder.get();
        retval.setLength(0); // clear/reset the buffer
        return retval;
    }

    static class InternalThreadLocal extends ThreadLocal<StringBuilder> {
        @Override
        protected StringBuilder initialValue() {
            return new StringBuilder();
        }
    }
}
