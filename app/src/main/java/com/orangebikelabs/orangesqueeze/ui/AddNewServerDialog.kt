/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */
package com.orangebikelabs.orangesqueeze.ui

import android.view.KeyEvent
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.google.common.net.HostAndPort
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess
import com.orangebikelabs.orangesqueeze.database.deleteServer
import com.orangebikelabs.orangesqueeze.databinding.AddNewServerBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * UI component to add a new server.<br></br>
 *
 * @author tbsandee@orangebikelabs.com
 */
class AddNewServerDialog private constructor(private val activity: FragmentActivity) {
    companion object {
        fun create(activity: FragmentActivity): MaterialDialog {
            val container = AddNewServerDialog(activity)
            return container.create()
        }
    }

    private lateinit var bindings: AddNewServerBinding
    private var lastCreateDisposable: Disposable? = null

    fun create(): MaterialDialog {
        val dialog = MaterialDialog(activity, BottomSheet()).apply {
            lifecycleOwner(activity)
            title(R.string.connectserver_title)
            cancelable(true)
            customView(viewRes = R.layout.add_new_server, scrollable = true)
            noAutoDismiss()
            positiveButton(res = R.string.connect_button) {
                onConnectButtonClicked(it)
            }
            negativeButton(res = R.string.cancel) {
                it.dismiss()
            }
            bindings = AddNewServerBinding.bind(getCustomView())
        }
        bindings.hostname.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    onConnectButtonClicked(dialog)
                }
                // absorb action_up as well
                true
            } else {
                false
            }
        }
        var ipAddress = DeviceInterfaceInfo.getInstance().mIpAddress
        if (ipAddress == null) {
            ipAddress = "<unknown>"
        }
        bindings.ipAddresses.text = activity.getString(R.string.ip_address, ipAddress)
        return dialog
    }

    private fun onConnectButtonClicked(dialog: MaterialDialog) {
        var validHostname = false
        val hostname = bindings.hostname.text.toString().trim()
        val port = bindings.port.text.toString().trim()
        if (hostname.isNotEmpty()) {
            validHostname = try {
                val hap = HostAndPort.fromParts(hostname, port.toIntOrNull() ?: 0)
                createNewServer(hap)
                true
            } catch (e: IllegalArgumentException) { // invalid hostname format
                false
            }
        }
        if (!validHostname) {
            MaterialDialog(activity).show {
                lifecycleOwner(activity)
                icon(res = android.R.drawable.ic_dialog_alert)
                title(R.string.connection_error_title)
                message(R.string.invalid_hostname_port)
            }
        } else {
            dialog.dismiss()
        }
    }

    private fun createNewServer(hostAndPort: HostAndPort) {
        if (lastCreateDisposable != null && !lastCreateDisposable!!.isDisposed) {
            return
        }
        lastCreateDisposable = Completable
                .fromRunnable {
                    val host = try { // check hostname
                        InetAddress.getByName(hostAndPort.host)
                        // actually put hostname into IP and let
                        // http requests resolve by hostname
                        hostAndPort.host
                    } catch (e: UnknownHostException) {
                        throw Exception(activity.getString(R.string.error_cannot_resolve_hostname, hostAndPort.host))
                    }
                    DatabaseAccess.getInstance(activity).let { da ->
                        val sq = da.serverQueries
                        da.transaction {
                            val server = sq.lookupByName(host).executeAsOneOrNull()
                            if (server?.servertype == ServerType.DISCOVERED) {
                                da.deleteServer(server._id)
                            }
                            if (sq.lookupByName(host).executeAsOneOrNull() == null) {
                                sq.insertSimple(serverhost = host, servername = host, serverport = hostAndPort.getPortOrDefault(9000), servertype = ServerType.PINNED)
                            } else {
                                throw Exception(activity.getString(R.string.hostname_already_exists, host))
                            }
                        }
                    }
                }
                .subscribeOn(OSExecutors.singleThreadScheduler())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onError = { error: Throwable ->
                    MaterialDialog(activity).show {
                        lifecycleOwner(activity)
                        icon(res = android.R.drawable.ic_dialog_alert)
                        title(res = R.string.connection_error_title)
                        message(text = error.message)
                    }
                })
    }
}