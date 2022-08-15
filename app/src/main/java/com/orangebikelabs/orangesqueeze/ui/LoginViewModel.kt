/*
 * Copyright (c) 2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.orangebikelabs.orangesqueeze.app.PendingConnection
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.common.event.PendingConnectionState
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess
import com.squareup.otto.Subscribe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.UnsupportedEncodingException
import java.security.GeneralSecurityException

class LoginViewModel(application: Application, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : AndroidViewModel(application) {

    // required for all viewmodels
    @Suppress("unused")
    constructor(application: Application) : this(application, Dispatchers.IO)

    private val database by lazy { DatabaseAccess.getInstance(application) }

    data class UsernamePasswordResult(val username: String, val password: String)

    sealed class Events {
        data class Login(val success: Boolean) : Events()
    }

    private var currentPendingConnection: PendingConnection? = null

    private val eventReceiver = object : Any() {
        @Subscribe
        fun whenPendingConnectionStateChanges(state: PendingConnectionState) {
            // ensures we have the current pending connection state at any given moment
            currentPendingConnection = state.pendingConnection
        }
    }

    val events = MutableLiveData<Event<Events>>()

    init {
        BusProvider.getInstance().register(eventReceiver)
    }

    suspend fun loadUsernamePassword(serverId: Long): UsernamePasswordResult? {
        var username = ""
        var password = ""
        var success = false
        withContext(ioDispatcher) {
            val serverRecord = database.serverQueries.lookupById(serverId).executeAsOneOrNull()
            if (serverRecord != null) {
                username = serverRecord.serverusername ?: ""
                if (serverRecord.serverpassword != null && serverRecord.serverkey != null) {
                    try {
                        val decrypted = EncryptionTools.decrypt(serverRecord.serverkey, serverRecord.serverpassword)
                        password = String(decrypted, Charsets.UTF_8)

                    } catch (e: GeneralSecurityException) {
                        OSLog.e("encryption error", e)
                    } catch (e: UnsupportedEncodingException) {
                        OSLog.e("encryption error", e)
                    }
                }
                success = true
            }
        }
        return if (success) {
            UsernamePasswordResult(username, password)
        } else {
            null
        }
    }

    fun setLoginCredentials(username: String, password: String) {
        currentPendingConnection?.apply {
            val future = setConnectionCredentials(username, password)
            // called when response is received from service about authentication
            Futures.addCallback(future, object : FutureCallback<Boolean> {
                override fun onSuccess(result: Boolean) {
                    events.value = Event(Events.Login(result))
                }

                override fun onFailure(e: Throwable) {
                    OSLog.w(e.message, e)
                    onSuccess(false)
                }
            }, OSExecutors.getMainThreadExecutor())
        }
    }

    override fun onCleared() {
        super.onCleared()

        BusProvider.getInstance().unregister(eventReceiver)
    }
}