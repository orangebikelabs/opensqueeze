/*
 * Copyright (c) 2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

/* math routines adapted from kotlin */

@SuppressWarnings("ManualMinMaxCalculation")
public class MoreMath {
    static public int coerceIn(int value, int min, int max) {
        if (min > max) throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum " + max + " is less than minimum " + min  + ".");
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    static public float coerceIn(float value, float min, float max) {
        if (min > max) throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum " + max + " is less than minimum " + min  + ".");
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
