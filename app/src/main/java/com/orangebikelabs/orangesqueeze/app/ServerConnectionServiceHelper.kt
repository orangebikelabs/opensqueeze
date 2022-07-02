/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.UiThread
import com.orangebikelabs.orangesqueeze.BuildConfig
import com.orangebikelabs.orangesqueeze.common.SBContextProvider

/**
 * Helper class to manage the connections state for the server connections.
 */
class ServerConnectionServiceHelper(val uiComponent: Boolean) {

    val isConnected: Boolean
        get() = service != null

    private var service: ServerConnectionService.LocalBinder? = null

    private var uiRefAdded = false
    private var serviceBound = false

    private val context by lazy { SBContextProvider.get().applicationContext }

    @UiThread
    fun onStart() {
        // Bind to ServerConnectionService
        val bindIntent = ServerConnectionService.getIntent(context, ServerConnectionService.ServiceActions.BIND_TO_SERVICE)

        var flags = Context.BIND_AUTO_CREATE

        if (BuildConfig.DEBUG) {
            flags = flags or Context.BIND_DEBUG_UNBIND
        }
        context.bindService(bindIntent, serviceConnection, flags)
        serviceBound = true
    }

    @UiThread
    fun onStop() {
        service?.let {
            if (uiRefAdded) {
                uiRefAdded = false
                it.removeUiRef()
            }
            service = null
        }
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private val serviceConnection = object : ServiceConnection {
        @UiThread
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            // could cast to our local service, but we don't use it
            service = (binder as ServerConnectionService.LocalBinder).apply {
                if (uiComponent) {
                    uiRefAdded = true
                    addUiRef()
                }
            }
        }

        @UiThread
        override fun onServiceDisconnected(component: ComponentName) {
            service = null
        }
    }
}
