/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.common

import android.content.Context
import android.database.ContentObservable
import android.util.Log
import androidx.annotation.Keep
import com.fasterxml.jackson.databind.JsonNode
import io.reactivex.rxjava3.core.Single
import java.io.File

/**
 * Common logging functions. Verbose and debug logging will not be stripped out for release and preview builds, but will be disabled by
 * default.
 *
 * @author tsandee
 */
class OSLog {
    companion object {
        @Volatile
        private var debugLoggingEnabled = false

        /**
         * can be called for force logging to a higher level at runtime
         */

        @JvmStatic
        fun enableDebugLogging() {
            debugLoggingEnabled = true
        }

        const val VERBOSE = Log.VERBOSE
        const val DEBUG = Log.DEBUG
        const val INFO = Log.INFO
        const val WARN = Log.WARN
        const val ERROR = Log.ERROR

        fun init(context: Context) {
            LogHandlers.init(context)
        }

        val contentObservable: ContentObservable
            get() = LogHandlers.getLogObservable()

        fun clear() {
            LogHandlers.clear()
        }

        @JvmStatic
        val logFile: Single<File>
            get() = LogHandlers.getConsolidatedLog()

        @JvmStatic
        fun isLoggable(tag: Tag, level: Int): Boolean {
            return Log.isLoggable(tag.tag, level) || (level >= VERBOSE && debugLoggingEnabled)
        }

        @JvmStatic
        fun isLoggable(level: Int): Boolean {
            return Log.isLoggable(Tag.DEFAULT.tag, level) || (level >= VERBOSE && debugLoggingEnabled)
        }

        @JvmStatic
        fun v(msg: String?) {
            v(Tag.DEFAULT, msg)
        }

        @JvmStatic
        fun v(msg: String?, t: Throwable?) {
            v(Tag.DEFAULT, msg, t)
        }

        @JvmStatic
        fun v(tag: Tag, msg: String?) {
            if (isLoggable(tag, VERBOSE)) {
                LogHandlers.add(VERBOSE, tag.tag, msg, null)
            }
        }

        @JvmStatic
        fun v(tag: Tag, msg: String?, t: Throwable?) {
            if (isLoggable(tag, VERBOSE)) {
                LogHandlers.add(VERBOSE, tag.tag, msg, t)
            }
        }

        @JvmStatic
        fun d(msg: String?) {
            d(Tag.DEFAULT, msg)
        }

        @JvmStatic
        fun d(msg: String?, t: Throwable?) {
            d(Tag.DEFAULT, msg, t)
        }

        @JvmStatic
        fun d(tag: Tag, msg: String?) {
            if (isLoggable(tag, DEBUG)) {
                LogHandlers.add(DEBUG, tag.tag, msg, null)
            }
        }

        @JvmStatic
        fun d(tag: Tag, msg: String?, t: Throwable?) {
            if (isLoggable(tag, DEBUG)) {
                LogHandlers.add(DEBUG, tag.tag, msg, t)
            }
        }

        @JvmStatic
        fun i(msg: String?) {
            i(Tag.DEFAULT, msg)
        }

        @JvmStatic
        fun i(msg: String?, t: Throwable?) {
            i(Tag.DEFAULT, msg, t)
        }

        @JvmStatic
        fun i(tag: Tag, msg: String?) {
            if (isLoggable(tag, INFO)) {
                LogHandlers.add(INFO, tag.tag, msg, null)
            }
        }

        @JvmStatic
        fun i(tag: Tag, msg: String?, t: Throwable?) {
            if (isLoggable(tag, INFO)) {
                LogHandlers.add(INFO, tag.tag, msg, t)
            }
        }

        @JvmStatic
        fun w(msg: String?) {
            w(Tag.DEFAULT, msg)
        }

        @JvmStatic
        fun w(msg: String?, t: Throwable?) {
            w(Tag.DEFAULT, msg, t)
        }

        @JvmStatic
        fun w(tag: Tag, msg: String?) {
            if (isLoggable(tag, WARN)) {
                LogHandlers.add(WARN, tag.tag, msg, null)
            }
        }

        @JvmStatic
        fun w(tag: Tag, msg: String?, t: Throwable?) {
            if (isLoggable(tag, WARN)) {
                LogHandlers.add(WARN, tag.tag, msg, t)
            }
        }

        @JvmStatic
        fun e(msg: String?) {
            e(Tag.DEFAULT, msg)
        }

        @JvmStatic
        fun e(msg: String?, t: Throwable?) {
            e(Tag.DEFAULT, msg, t)
        }

        @JvmStatic
        fun e(tag: Tag, msg: String?) {
            if (isLoggable(tag, ERROR)) {
                LogHandlers.add(ERROR, tag.tag, msg, null)
            }
        }

        @JvmStatic
        fun e(tag: Tag, msg: String?, t: Throwable?) {
            if (isLoggable(tag, ERROR)) {
                LogHandlers.add(ERROR, tag.tag, msg, t)
            }
        }

        @JvmStatic
        fun jsonTrace(msg: String?, node: JsonNode?) {
            jsonlog(VERBOSE, Tag.JSONTRACE, msg, node, null)
        }

        @JvmStatic
        fun v(tag: Tag, msg: String?, node: JsonNode?) {
            jsonlog(VERBOSE, tag, msg, node, null)
        }

        @JvmStatic
        fun i(tag: Tag, msg: String?, node: JsonNode?) {
            jsonlog(INFO, tag, msg, node, null)
        }

        @JvmStatic
        fun i(tag: Tag, msg: String?, node: JsonNode?, e: Throwable?) {
            jsonlog(INFO, tag, msg, node, e)
        }

        @JvmStatic
        fun d(tag: Tag, msg: String?, node: JsonNode?) {
            jsonlog(DEBUG, tag, msg, node, null)
        }

        @JvmStatic
        fun e(tag: Tag, msg: String?, node: JsonNode?) {
            jsonlog(ERROR, tag, msg, node, null)
        }

        @JvmStatic
        fun e(tag: Tag, msg: String?, node: JsonNode?, e: Throwable?) {
            jsonlog(ERROR, tag, msg, node, e)
        }

        @JvmStatic
        fun w(tag: Tag, msg: String?, node: JsonNode?) {
            jsonlog(WARN, tag, msg, node, null)
        }

        @JvmStatic
        fun w(tag: Tag, msg: String?, node: JsonNode?, e: Throwable?) {
            jsonlog(WARN, tag, msg, node, e)
        }

        private fun jsonlog(level: Int, tag: Tag, msg: String?, node: JsonNode?, throwable: Throwable?) {
            if (isLoggable(tag, level)) {
                LogHandlers.add(level, tag.tag, msg, node, throwable)
            }
        }
    }

    @Keep
    enum class Tag(val tag: String) {
        DEFAULT("OpenSqueeze"), CACHE("OSCache"), ARTWORK("OSArtwork"),
        NETWORK("OSNetwork"), NETWORKTRACE("OSNetTrace"), JSONTRACE("JsonTrace"), LOADERS("OSLoaders"),
        TIMING("OSTiming");

        fun newTimingLogger(name: String): TimingLoggerCompat {
            return TimingLoggerCompat(name)
        }
    }

    class TimingLoggerCompat(val name: String) : AutoCloseable {
        fun addSplit(name: String) {

        }

        override fun close() {
        }
    }
}