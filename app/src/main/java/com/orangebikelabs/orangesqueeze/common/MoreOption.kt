/*
 * Copyright (c) 2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common

import arrow.core.Option
import arrow.core.getOrElse

/** helper functions allowing better Java interrop with Arrow Option class */
class MoreOption {
    companion object {

        /** return option value, or default if null */
        @JvmStatic
        fun <T> getOrElse(o1: Option<T>, default: T): T {
            return o1.getOrElse { default }
        }

        /** return option value or throw assertion */
        @JvmStatic
        fun <T> get(o1: Option<T>): T {
            return requireNotNull(o1.orNull())
        }
    }
}