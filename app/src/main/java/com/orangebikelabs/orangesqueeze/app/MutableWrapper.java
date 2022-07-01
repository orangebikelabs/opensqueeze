/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
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
