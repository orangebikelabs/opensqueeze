/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */
package com.orangebikelabs.orangesqueeze.startup

import android.os.Bundle
import androidx.fragment.app.commitNow
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.SBActivity
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo
import com.orangebikelabs.orangesqueeze.databinding.ToolbarActivityBinding
import com.orangebikelabs.orangesqueeze.databinding.ToolbarBinding

/**
 * Activity that presents a list of servers to connect with.
 *
 * @author tbsandee@orangebikelabs.com
 */
open class ConnectActivity : SBActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ToolbarActivityBinding.inflate(layoutInflater)
        val toolbarBinding = ToolbarBinding.bind(binding.root)
        contentView = binding.root

        setSupportActionBar(toolbarBinding.toolbar)
        supportActionBar?.hide()

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.toolbar_content, ConnectFragment(), null)
            }
        }
    }

    override fun isSupportedConnectionState(ci: ConnectionInfo): Boolean {
        return !ci.isConnected
    }

    override fun showConnectingDialog() {
        // nope!
    }

    override fun showConnectivityDialog() {
        // nope!
    }
}