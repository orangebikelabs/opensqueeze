/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.ui

import android.content.Context
import android.view.KeyEvent
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.google.common.net.HostAndPort
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.databinding.AddNewServerBinding
import com.orangebikelabs.orangesqueeze.startup.ConnectViewModel
import kotlinx.coroutines.*

/**
 * UI component to add a new server.<br></br>
 *
 * @author tbsandee@orangebikelabs.com
 */

class AddNewServerDialog private constructor(private val context: Context, private val lifecycleOwner: LifecycleOwner, private val viewModel: ConnectViewModel) {
    sealed class Result
    data class Success(val id: Long) : Result()
    data class Failure(val message: String): Result()

    companion object {
        fun create(fragment: Fragment, viewModel: ConnectViewModel): AddNewServerDialog {
            val container = AddNewServerDialog(fragment.requireContext(), fragment.viewLifecycleOwner, viewModel)
            container.create()
            return container
        }
    }

    private lateinit var bindings: AddNewServerBinding
    private lateinit var dialog: MaterialDialog
    private val deferredResult = CompletableDeferred<Result>()

    suspend fun show(): Result {
        dialog.show()
        return deferredResult.await()
    }

    fun create() {
        dialog = MaterialDialog(context, BottomSheet())
        dialog
                .lifecycleOwner(lifecycleOwner)
                .title(R.string.connectserver_title)
                .cancelable(true)
                .customView(viewRes = R.layout.add_new_server, scrollable = true)
                .noAutoDismiss()
                .positiveButton(res = R.string.connect_button) {
                    onConnectButtonClicked()
                }
                .negativeButton(res = R.string.cancel) {
                    it.dismiss()
                }
                .onDismiss {
                    if (!deferredResult.isCompleted) {
                        deferredResult.complete(Failure("cancelled"))
                    }
                }
        bindings = AddNewServerBinding.bind(dialog.getCustomView())
        bindings.hostname.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    onConnectButtonClicked()
                }
                // absorb action_up as well
                true
            } else {
                false
            }
        }

        val ipAddress = viewModel.getCurrentIpAddress()
        bindings.ipAddresses.text = context.getString(R.string.ip_address, ipAddress)
    }

    private fun onConnectButtonClicked() {
        when(val result = viewModel.parseHostAndPort(bindings.hostname.text.toString(), bindings.port.text.toString())) {
            is ConnectViewModel.ParseHostAndPortResult.Failure -> {
                showInvalidHostnameOrPortDialog()
            }
            is ConnectViewModel.ParseHostAndPortResult.Success -> {
                createNewServer(result.hostAndPort)
            }
        }
    }

    private fun createNewServer(hostAndPort: HostAndPort) {
        lifecycleOwner.lifecycleScope.launch {
            val valid = viewModel.validateHost(hostAndPort.host)
            if (!valid) {
                showConnectionErrorDialog(hostAndPort.host)
                return@launch
            }
            when(val result = viewModel.createNewServer(hostAndPort)) {
                is ConnectViewModel.CreateNewServerResult.Success -> {
                    // success!
                    deferredResult.complete(Success(result.serverId))
                    dialog.dismiss()
                }
                is ConnectViewModel.CreateNewServerResult.Failure -> {
                    showExceptionDialog(result.message)
                }
            }
        }
    }

    private fun showExceptionDialog(error: String) {
        MaterialDialog(context).show {
            lifecycleOwner(lifecycleOwner)
            icon(res = android.R.drawable.ic_dialog_alert)
            title(res = R.string.connection_error_title)
            message(text = error)
        }
    }

    private fun showInvalidHostnameOrPortDialog() {
        MaterialDialog(context).show {
            lifecycleOwner(lifecycleOwner)
            icon(res = android.R.drawable.ic_dialog_alert)
            title(R.string.connection_error_title)
            message(R.string.invalid_hostname_port)
        }
    }

    private fun showConnectionErrorDialog(hostname: String) {
        MaterialDialog(context).show {
            lifecycleOwner(lifecycleOwner)
            icon(res = android.R.drawable.ic_dialog_alert)
            title(res = R.string.connection_error_title)
            message(text = context.getString(R.string.error_cannot_resolve_hostname, hostname))
        }
    }
}