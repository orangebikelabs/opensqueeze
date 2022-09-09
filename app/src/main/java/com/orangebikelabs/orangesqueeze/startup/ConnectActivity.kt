/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.startup

import android.os.Bundle
import android.view.View
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

    // avoid use of lateinit because sometimes snackbar notice comes before onCreate() and it would crash
    protected var binding: ToolbarActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ToolbarActivityBinding.inflate(layoutInflater).also {
            setContentView(it.root)

            val toolbarBinding = ToolbarBinding.bind(it.root)
            setSupportActionBar(toolbarBinding.toolbar)
            supportActionBar?.hide()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.toolbar_content, ConnectFragment(), null)
            }
        }
    }

    override fun getSnackbarView(): View? {
        return binding?.toolbarContent
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