/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.database

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.orangebikelabs.orangesqueeze.common.OSExecutors.SafeThreadPoolExecutor
import com.orangebikelabs.orangesqueeze.common.ScrollingState
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Thread pool executor that has a fixed queue of requests, after which caller-runs policy kicks in.
 * Additionally, new tasks will not start executing if we are mid-scroll.
 */
class DatabaseWriterThreadPoolExecutor private constructor(queueSize: Int, threadNameFormat: String) :
        SafeThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                ArrayBlockingQueue(queueSize), Thread.NORM_PRIORITY - 1, threadNameFormat) {
    override fun beforeExecute(t: Thread, r: Runnable) {
        try {
            ScrollingState.waitForNotScrolling(30, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            // shouldn't happen
        }
        super.beforeExecute(t, r)
    }

    companion object {
        @JvmStatic
        fun newInstance(queueSize: Int, threadNameFormat: String): ListeningExecutorService {
            val retval = DatabaseWriterThreadPoolExecutor(queueSize, threadNameFormat)
            retval.rejectedExecutionHandler = CallerRunsPolicy()
            return MoreExecutors.listeningDecorator(retval)
        }
    }
}