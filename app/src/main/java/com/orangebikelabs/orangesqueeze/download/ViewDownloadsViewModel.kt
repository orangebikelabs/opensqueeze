/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.orangebikelabs.orangesqueeze.common.SBContextProvider
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess
import com.orangebikelabs.orangesqueeze.database.LookupDownloadsWithServerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class ViewDownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val database by lazy { DatabaseAccess.getInstance(application) }

    @OptIn(FlowPreview::class)
    val downloads: Flow<List<LookupDownloadsWithServerId>>
        get() = database.downloadQueries
                .lookupDownloadsWithServerId(SBContextProvider.get().serverId)
                .asFlow()
                .mapToList(Dispatchers.IO)
                .sample(250)

    fun clearDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            database.downloadQueries.deleteAll()
        }
    }
}