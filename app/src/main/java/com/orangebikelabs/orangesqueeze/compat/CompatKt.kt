/*
 * Copyright (c) 2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.compat

import android.annotation.SuppressLint
import android.app.Service
import android.os.Build.VERSION

class CompatKt {
    companion object {
        @SuppressLint("InlinedApi")
        const val STOP_FOREGROUND_REMOVE = Service.STOP_FOREGROUND_REMOVE
        @SuppressLint("InlinedApi")
        const val STOP_FOREGROUND_DETACH = Service.STOP_FOREGROUND_DETACH
    }
}

fun Service.stopForegroundCompat(flags: Int) {
    if (VERSION.SDK_INT >= 24) {
        this.stopForeground(flags)
    } else {
        when (flags) {
            Service.STOP_FOREGROUND_REMOVE,
            Service.STOP_FOREGROUND_DETACH -> {
                @Suppress("DEPRECATION")
                this.stopForeground(true)
            }
            0 -> {
                @Suppress("DEPRECATION")
                this.stopForeground(false)
            }
            else -> throw IllegalStateException()
        }
    }
}