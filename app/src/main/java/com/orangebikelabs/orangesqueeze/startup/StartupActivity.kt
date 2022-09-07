/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.startup

import android.content.Intent
import android.os.Bundle
import com.orangebikelabs.orangesqueeze.app.SBActivity
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo
import com.orangebikelabs.orangesqueeze.common.LaunchFlags
import com.orangebikelabs.orangesqueeze.common.SBPreferences
import com.orangebikelabs.orangesqueeze.net.DeviceConnectivity

/**
 * @author tbsandee@orangebikelabs.com
 */
class StartupActivity : SBActivity() {
    companion object {
        private const val CHECK_UPGRADE_EXTRA = "checkUpgrade"
    }

    override fun isSupportedConnectionState(state: ConnectionInfo): Boolean {
        // supports no connection states, immediately we go to other activity
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LaunchFlags.reset()

        val segments = intent?.data?.pathSegments
        if (segments == listOf("orangesqueeze", "nowplaying")) {
            LaunchFlags.gotoNowPlaying = true
        }
        // unique to startupactivity, check connection state onCreate()
        checkConnectionState()
    }

    override fun checkConnectionState(): Boolean {
        val ci = mContext.connectionInfo

        var doUpgrade = false
        if (intent == null || intent.getBooleanExtra(CHECK_UPGRADE_EXTRA, true)) {
            if (SBPreferences.get().isFirstLaunch) {
                SBPreferences.get().updateLastRunVersionCode()
            } else if (SBPreferences.get().shouldUpgradeFirstLaunch) {
                doUpgrade = true
            }
        }
        if(doUpgrade) {
            handleUpgrade()
        }
        if (!ci.isConnected && mContext.isConnecting) {
            logVerbose("checkConnectionState ci=$ci, connecting=true, launchConnectingActivity")
            startActivity(Intent(this, ConnectingActivity::class.java))

            finish()
            return true
        } else if (!DeviceConnectivity.instance.deviceConnectivity) {
            // force bump to connect activity immediately to avoid sporadic crash due to not leaving startupactivity early enough
            logVerbose("checkConnectionState no connectivity in startupActivity")
            startActivity(Intent(this, ConnectActivity::class.java))
            finish()
            return true
        } else if (getInstanceCount() > 1) {
            finish()
            return true
        } else {
            return super.checkConnectionState()
        }
    }

    private fun handleUpgrade() {
        // insert future upgrade logic here, if necessary
    }
}
