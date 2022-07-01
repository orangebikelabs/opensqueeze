/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.common

import com.fasterxml.jackson.databind.JsonNode
import com.orangebikelabs.orangesqueeze.BuildConfig
import com.orangebikelabs.orangesqueeze.common.OSLog.Companion.w

/**
 * @author tsandee
 */
object Reporting {
    @JvmStatic
    fun report(message: String?) {
        report(null, message, null)
    }

    @JvmStatic
    fun reportIfDebug(message: String?) {
        if (BuildConfig.DEBUG) {
            report(message)
        }
    }

    @JvmStatic
    fun report(t: Throwable?) {
        report(t, null, null)
    }

    @JvmStatic
    fun report(t: Throwable?, message: String?) {
        report(t, message, null)
    }

    @JvmStatic
    fun reportIfDebug(t: Throwable?, message: String?) {
        if (BuildConfig.DEBUG) {
            report(t, message)
        }
    }

    @JvmStatic
    fun report(e: Throwable?, message: String?, extraInfo: Any?) {
        val effectiveThrowable: Throwable?
        effectiveThrowable = if (message != null) {
            if (e == null) {
                Exception(message)
            } else {
                Exception(message, e)
            }
        } else if (e != null) {
            Exception("Reported exception", e)
        } else {
            null
        }
        if (effectiveThrowable != null) {
            if (extraInfo is JsonNode) {
                w(OSLog.Tag.DEFAULT, effectiveThrowable.message, extraInfo as JsonNode?, effectiveThrowable)
            } else {
                w(OSLog.Tag.DEFAULT, effectiveThrowable.message, effectiveThrowable)
            }
        }
    }
}