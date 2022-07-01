/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.app

import android.content.Context
import com.orangebikelabs.orangesqueeze.common.InterruptedAwareRunnable
import com.orangebikelabs.orangesqueeze.common.PlayerId
import com.orangebikelabs.orangesqueeze.common.SBContextProvider
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess

/**
 * @author tbsandee@orangebikelabs.com
 */
class AfterPlayerChangedRunnable(private val context: Context, private val serverId: Long, private val playerId: PlayerId) : InterruptedAwareRunnable() {

    override fun doRun() {
        // wait for connection
        if (SBContextProvider.get().awaitConnection("AfterPlayerChangedRunnable")) {
            DatabaseAccess.getInstance(context)
                    .serverQueries
                    .updateLastPlayer(playerId, serverId)
        }
    }

}