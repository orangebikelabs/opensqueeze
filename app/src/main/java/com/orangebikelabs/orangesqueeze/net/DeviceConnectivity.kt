/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import androidx.core.content.ContextCompat
import com.orangebikelabs.orangesqueeze.common.OSAssert
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** class that encapsulates our limited view of device connectivity in the app */
class DeviceConnectivity private constructor(context: Context) {
    companion object {
        lateinit var instance: DeviceConnectivity
            private set

        fun init(context: Context) {
            instance = DeviceConnectivity(context)

            val filter = IntentFilter()
            @Suppress("DEPRECATION")
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            ContextCompat.registerReceiver(context, ConnectivityBroadcastReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    private object ConnectivityBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                @Suppress("DEPRECATION")
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    // do nothing
                    instance.updateDeviceConnectivity()
                }
            }
        }
    }

    private val connectivityManager = checkNotNull(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?) {
        "connectivity manager should never be null"
    }

    private val deviceConnectivitySubject = BehaviorSubject.createDefault(false)

    init {
        updateDeviceConnectivity()
    }

    val deviceConnectivity: Boolean
        get() {
            return checkNotNull(deviceConnectivitySubject.value)
        }

    fun observeDeviceConnectivity(): Observable<Boolean> {
        return deviceConnectivitySubject.distinctUntilChanged()
    }

    fun awaitNetwork(time: Long, units: TimeUnit): Boolean {
        OSAssert.assertNotMainThread()
        return try {
            deviceConnectivitySubject
                    .filter { it }
                    .firstOrError()
                    .toFuture()
                    .get(time, units)
        } catch (e: TimeoutException) {
            false
        }
    }

    @Synchronized
    private fun updateDeviceConnectivity() {
        @Suppress("DEPRECATION") val activeNetwork = connectivityManager.activeNetworkInfo
        @Suppress("DEPRECATION") val value = activeNetwork?.isConnectedOrConnecting == true
        deviceConnectivitySubject.onNext(value)
    }
}