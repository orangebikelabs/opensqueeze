/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */
package com.orangebikelabs.orangesqueeze.startup

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import com.orangebikelabs.orangesqueeze.app.PendingConnection
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo
import com.orangebikelabs.orangesqueeze.common.event.PendingConnectionState
import com.orangebikelabs.orangesqueeze.net.DeviceConnectivity
import com.orangebikelabs.orangesqueeze.ui.MainActivity
import com.squareup.otto.Subscribe

/**
 * Activity that presents a list of servers to connect with. This is used when we are already connected to another server.
 *
 * @author tbsandee@orangebikelabs.com
 */
class SwitchServerActivity : ConnectActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.apply {
            show()
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }
        mBus.register(mEventReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        mBus.unregister(mEventReceiver)
    }

    override fun isSupportedConnectionState(ci: ConnectionInfo): Boolean {
        return ci.isConnected
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            val upIntent = Intent(this, MainActivity::class.java)
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is not part of the application's task, so create a new task with a synthesized back stack.
                TaskStackBuilder.create(this).addNextIntent(upIntent).startActivities()
                finish()
            } else { // This activity is part of the application's task, so simply navigate up to the hierarchical parent activity.
                finish()
            }
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    /**
     * returns whether or normal processing should continue
     */
    override fun checkConnectionState(): Boolean {
        val ci = mContext.connectionInfo
        logVerbose("checkConnectionState %s", ci)
        if (isFinishing) {
            logVerbose("checkConnectionState finishing $ci")
            return false
        }
        var ok = true
        if (!DeviceConnectivity.instance.deviceConnectivity) {
            logVerbose("no device connectivity")
            showConnectivityDialog()
        } else { // yes, connectivity!
            dismissConnectivityDialog()
            if (!isSupportedConnectionState(ci)) {
                finish()
                ok = false
            }
        }
        return ok
    }

    private val mEventReceiver: Any = object : Any() {
        @Subscribe
        fun whenPendingConnectionChanged(state: PendingConnectionState) {
            val connection = state.pendingConnection ?: return
            if (connection.state == PendingConnection.PendingState.SUCCESS) {
                // the fragment will take care of finalizing the connection, we just need to relaunch the main activity
                finish()
            }
        }
    }
}