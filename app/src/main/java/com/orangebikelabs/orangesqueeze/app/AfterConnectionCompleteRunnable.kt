/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.app

import android.content.Context
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess
import com.orangebikelabs.orangesqueeze.download.DownloadService

/**
 * @author tbsandee@orangebikelabs.com
 */
class AfterConnectionCompleteRunnable(private val context: Context, private val connection: PendingConnection) : Runnable {
    private val serverQueries = DatabaseAccess.getInstance(context).serverQueries

    override fun run() {
        val serverId = connection.serverId
        serverQueries.transaction {
            val ci = connection.connectedInfo

            // reset autoconnect for all servers except for current one to false
            serverQueries.lookupAll().executeAsList().iterator().forEach {
                serverQueries.updateAutoconnect(serverautoconnect = serverId == it._id, findserverid = it._id)
            }
            // get the server mac address from the ARP cache, if we have it
            // and update the stored mac address
            serverQueries.updateWakeOnLan(serverwakeonlan = ci.wakeOnLanSettings, findserverid = serverId)
            connection.actualPlayerId?.let {
                serverQueries.updateLastPlayer(it, serverId)
            }
        }
        try {
            // start any downloads
            val downloadService = DownloadService.getStartDownloadsIntent(context.applicationContext, serverId)
            context.startService(downloadService)
        } catch (e: IllegalStateException) {
            // ignore, can happen when app is shutting down
        }
    }

}