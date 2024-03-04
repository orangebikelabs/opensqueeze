/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.startup

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.*
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.google.common.net.HostAndPort
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.PendingConnection
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.common.event.PendingConnectionState
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess
import com.orangebikelabs.orangesqueeze.database.deleteServer
import com.orangebikelabs.orangesqueeze.database.Server
import com.orangebikelabs.orangesqueeze.net.SendDiscoveryPacketService
import com.squareup.otto.Subscribe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.IllegalStateException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class ConnectViewModel(application: Application, private val ioDispatcher: CoroutineDispatcher): AndroidViewModel(application) {

    // required for all viewmodels
    @Suppress("unused")
    constructor(application: Application) : this(application, Dispatchers.IO)

    private val database by lazy { DatabaseAccess.getInstance(application) }
    private val context by lazy { SBContextProvider.get() }

    private var sendDiscoveryPacket: SendDiscoveryPacketService? = null

    private var ignorePendingConnectionEvent = false

    sealed class Events {
        data class ConnectionFailed(val reason: String) : Events()
        data class ConnectionNeedsLogin(val connectionInfo: ConnectionInfo) : Events()
    }

    enum class ServerOperation(@StringRes val resId: Int) {
        REMOVE(R.string.menu_remove_server),
        REMOVE_CREDENTIALS(R.string.menu_remove_server_credentials),
        PIN(R.string.menu_pin_server),
        UNPIN(R.string.menu_unpin_server),
        WAKEONLANSETTINGS(R.string.menuitem_wakeonlansettings)
    }

    val events: MutableLiveData<Event<Events>> = MutableLiveData()

    val servers: Flow<List<Server>>
        get() =
            database.serverQueries
                    .lookupForServerList(
                            serverlastseen = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5),
                            discoveredservertype = ServerType.DISCOVERED
                    )
                    .asFlow()
                    .mapToList(Dispatchers.IO)

    init {
        ignorePendingConnectionEvent = true
        BusProvider.getInstance().register(this)
        ignorePendingConnectionEvent = false
    }

    override fun onCleared() {
        super.onCleared()

        BusProvider.getInstance().unregister(this)

        sendDiscoveryPacket?.stopAsync()
        sendDiscoveryPacket = null
    }

    fun setDiscoveryMode(enabled: Boolean) {
        if(enabled && sendDiscoveryPacket == null) {
            sendDiscoveryPacket = SendDiscoveryPacketService(15, TimeUnit.SECONDS)
            sendDiscoveryPacket?.startAsync()
        } else if(!enabled && sendDiscoveryPacket != null) {
            sendDiscoveryPacket?.stopAsync()
            sendDiscoveryPacket = null
        }
    }

    fun getAvailableServerOperations(server: Server): List<ServerOperation> {
        val hasCredentials = server.serverkey != null
        val retval = mutableListOf<ServerOperation>()
        ServerOperation.entries.forEach {
            val available = when (it) {
                ServerOperation.REMOVE -> true
                ServerOperation.REMOVE_CREDENTIALS -> hasCredentials
                ServerOperation.PIN -> server.servertype == ServerType.DISCOVERED
                ServerOperation.UNPIN -> server.servertype == ServerType.PINNED
                ServerOperation.WAKEONLANSETTINGS -> true
            }
            if (available) {
                retval += it
            }
        }
        return retval
    }

    fun getCurrentIpAddress(): String {
        return DeviceInterfaceInfo.getInstance().mIpAddress ?: "<unknown>"
    }

    /**
     * execute the context menu db operations on a background thread
     */
    fun performServerOperation(operation: ServerOperation, server: Server) {
        val serverId = server._id
        viewModelScope.launch(context = Dispatchers.IO) {
            val sq = database.serverQueries
            return@launch when (operation) {
                ServerOperation.REMOVE -> {
                    if (context.isConnected && serverId == context.serverId) {
                        context.disconnect()
                    }
                    database.deleteServer(serverId)
                }
                ServerOperation.REMOVE_CREDENTIALS -> {
                    sq.updateCredentials(null, null, null, null, serverId)
                }
                ServerOperation.PIN -> {
                    sq.updateServerTypeWithTypeCheck(ServerType.PINNED, serverId, ServerType.DISCOVERED)
                }
                ServerOperation.UNPIN -> {
                    if (server.servertype == ServerType.PINNED) {
                        database.deleteServer(serverId)
                    }
                    Unit
                }
                ServerOperation.WAKEONLANSETTINGS -> {
                    throw IllegalStateException("handled in view")
                }
            }
        }
    }

    sealed class ParseHostAndPortResult {
        data class Success(val hostAndPort: HostAndPort) : ParseHostAndPortResult()
        data class Failure(val errorMessage: String) : ParseHostAndPortResult()
    }

    fun parseHostAndPort(hostname: String, port: String): ParseHostAndPortResult {
        val checkHostname = hostname.trim()
        val checkPort = port.trim()
        if (checkHostname.isNotEmpty()) {
            return try {
                ParseHostAndPortResult.Success(HostAndPort.fromParts(checkHostname, checkPort.toIntOrNull() ?: 0))
            } catch (e: IllegalArgumentException) {
                // invalid hostname format
                ParseHostAndPortResult.Failure(e.message ?: "invalid hostname")
            }
        }
        return ParseHostAndPortResult.Failure("blank host")
    }

    suspend fun validateHost(host: String): Boolean {
        @Suppress("BlockingMethodInNonBlockingContext")
        return withContext(ioDispatcher) {
            try {
                // check hostname
                InetAddress.getByName(host)
                true
            } catch (e: UnknownHostException) {
                false
            }
        }
    }

    sealed class CreateNewServerResult {
        data class Success(val serverId: Long) : CreateNewServerResult()
        data class Failure(val message: String) : CreateNewServerResult()
    }
    suspend fun createNewServer(hostAndPort: HostAndPort): CreateNewServerResult {
        return withContext(ioDispatcher) {
            database.transactionWithResult {
                val host = hostAndPort.host
                val sq = database.serverQueries
                val server = sq.lookupByName(host).executeAsOneOrNull()
                if (server?.servertype == ServerType.DISCOVERED) {
                    database.deleteServer(server._id)
                }
                if (sq.lookupByName(host).executeAsOneOrNull() == null) {
                    sq.insertSimple(serverhost = host, servername = host, serverport = hostAndPort.getPortOrDefault(9000), servertype = ServerType.PINNED)
                    val serverId = database.globalQueries
                            .last_insert_rowid()
                            .executeAsOne()
                    CreateNewServerResult.Success(serverId)
                } else {
                    CreateNewServerResult.Failure(context.applicationContext.getString(R.string.hostname_already_exists, host))
                }
            }
        }
    }

    @Subscribe
    fun whenPendingConnectionChanged(event: PendingConnectionState) {
        if (ignorePendingConnectionEvent) return

        val pendingConnection = event.pendingConnection ?: return

        val state = pendingConnection.state
        val reason = pendingConnection.failureReason.orElseGet { "Error" }
        OSLog.v("ConnectFragment::whenPendingConnectionChanged $state, reason=$reason")
        when (state) {
            PendingConnection.PendingState.SUCCESS -> context.finalizePendingConnection()
            PendingConnection.PendingState.FAILED_ERROR -> events.postValue(Event(Events.ConnectionFailed(reason)))
            PendingConnection.PendingState.FAILED_NEED_CREDENTIALS -> events.postValue(Event(Events.ConnectionNeedsLogin(pendingConnection.connectedInfo)))
            else -> {
                // ignore
            }
        }
    }
}