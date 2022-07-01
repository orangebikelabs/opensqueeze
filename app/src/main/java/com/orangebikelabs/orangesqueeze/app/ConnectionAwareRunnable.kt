/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app

import com.orangebikelabs.orangesqueeze.common.OSLog
import com.orangebikelabs.orangesqueeze.common.SBContext
import com.orangebikelabs.orangesqueeze.common.SBContextProvider

/**
 * special runnable that will execute after waiting for the connection to be established
 */
class ConnectionAwareRunnable(private val location: String, private val fn: (context: SBContext) -> Unit) : Runnable {
    override fun run() {
        OSLog.d("connectionAwareRunnable::run")
        val context = SBContextProvider.get()
        context.startAutoConnect()
        try {
            OSLog.d("connectionAwareRunnable::waiting for connection")
            if (context.awaitConnection(location)) {
                OSLog.d("connectionAwareRunnable::found connection")
                fn(context)
            }
            OSLog.d("connectionAwareRunnable::done")
        } catch (e: InterruptedException) {
            // ignore
        }
    }
}