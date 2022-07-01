/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app;

import androidx.annotation.Keep;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep // because used with reflection
public interface MutableWrapper<T> {
    void setBase(T base);
}
