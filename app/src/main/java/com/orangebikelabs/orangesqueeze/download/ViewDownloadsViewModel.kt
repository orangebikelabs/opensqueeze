/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orangebikelabs.orangesqueeze.common.SBContextProvider
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess
import com.orangebikelabs.orangesqueeze.database.LookupDownloadsWithServerId
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class ViewDownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val database by lazy { DatabaseAccess.getInstance(application) }

    @FlowPreview
    val downloads: Flow<List<LookupDownloadsWithServerId>>
        get() = database.downloadQueries
                .lookupDownloadsWithServerId(SBContextProvider.get().serverId)
                .asFlow()
                .mapToList()
                .sample(250)

    fun clearDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            database.downloadQueries.deleteAll()
        }
    }
}