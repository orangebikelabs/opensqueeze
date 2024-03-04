/*
 * Copyright (c) 2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.compat

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build.VERSION
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat

class CompatKt {
    companion object {
        @SuppressLint("InlinedApi")
        const val STOP_FOREGROUND_REMOVE = Service.STOP_FOREGROUND_REMOVE
        @SuppressLint("InlinedApi")
        const val STOP_FOREGROUND_DETACH = Service.STOP_FOREGROUND_DETACH
    }
}

fun <T> Bundle.getParcelableCompat(key: String?, clz: Class<T>): T? {
    return if(VERSION.SDK_INT >= 33) {
        this.getParcelable(key, clz)
    } else {
        @Suppress("DEPRECATION")
        this.getParcelable(key)
    }
}

fun <T : Parcelable> Bundle.getParcelableArrayListCompat(key: String?, clz: Class<T>): ArrayList<T>? {
    return BundleCompat.getParcelableArrayList(this, key, clz)
}

fun <T : Parcelable> Intent.getParcelableArrayListExtraCompat(key: String?, clz: Class<T>): ArrayList<T>? {
    val extras = this.extras ?: return null

    return BundleCompat.getParcelableArrayList(extras, key, clz)
}

@Suppress("DEPRECATION")
fun Service.stopForegroundCompat(flags: Int) {
    if (VERSION.SDK_INT >= 24) {
        this.stopForeground(flags)
    } else {
        when (flags) {
            Service.STOP_FOREGROUND_REMOVE,
            Service.STOP_FOREGROUND_DETACH -> {
                this.stopForeground(true)
            }
            0 -> {
                this.stopForeground(false)
            }
            else -> throw IllegalStateException()
        }
    }
}

fun Service.startForegroundForMediaPlaybackCompat(id: Int, notification: Notification) {
    if(VERSION.SDK_INT >= 29) {
        this.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    } else {
        this.startForeground(id, notification)
    }
}