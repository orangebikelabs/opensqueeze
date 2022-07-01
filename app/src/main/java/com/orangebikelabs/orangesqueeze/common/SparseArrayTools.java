/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.util.SparseArray;

/**
 * Utility functions for working with sparse arrays
 *
 * @author tsandee
 */
public class SparseArrayTools {
    private SparseArrayTools() {
    }

    /**
     * copy keys from one sparsearray to another
     */
    public static <T> void copy( SparseArray<T> dest,  SparseArray<T> src) {
        int size = src.size();
        for (int i = 0; i < size; i++) {
            int k = src.keyAt(i);
            T v = src.valueAt(i);

            dest.put(k, v);
        }
    }
}
