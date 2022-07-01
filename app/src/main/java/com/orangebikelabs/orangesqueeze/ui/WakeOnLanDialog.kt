/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.ui

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.compat.Compat
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess
import com.orangebikelabs.orangesqueeze.databinding.WakeOnLanSettingsBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.IOException

/**
 * @author tbsandee@orangebikelabs.com
 */
class WakeOnLanDialog private constructor(private val lifecycleOwner: LifecycleOwner, private val context: Context, private val serverId: Long, private val serverName: String) {
    companion object {
        @JvmStatic
        fun newInstance(fragment: Fragment, serverId: Long, serverName: String): MaterialDialog {
            val dialog = WakeOnLanDialog(fragment, fragment.requireContext(), serverId, serverName)
            return dialog.create()
        }
    }

    private lateinit var bindings: WakeOnLanSettingsBinding

    private var settings = WakeOnLanSettings()
    private var populateDisposable: Disposable? = null
    private var cancelled = false
    private var dismissed = false

    private fun create(): MaterialDialog {
        cancelled = false
        return MaterialDialog(context).apply {
            lifecycleOwner(lifecycleOwner)
            setTitle(context.getString(R.string.wakeonlan_dialog_title, serverName))
            cancelable(true)
            positiveButton(res = R.string.ok) { onPositiveButtonClicked() }
            negativeButton(res = R.string.cancel)
            customView(viewRes = R.layout.wake_on_lan_settings)
            @Suppress("DEPRECATION")
            (neutralButton(res = R.string.reset) {
                onResetClicked()
            })
            setOnShowListener {
                populateSettings()
            }
            setOnCancelListener {
                cancelled = true
            }
            setOnDismissListener {
                populateDisposable?.dispose()
                dismissed = true
            }
            bindings = WakeOnLanSettingsBinding.bind(getCustomView())
            bindings.autodetect.setOnCheckedChangeListener { _, _ ->
                resetEnabledFlags()
            }

            if (!Compat.isArpLookupAllowed()) {
                bindings.autodetect.visibility = View.GONE
                bindings.autodetect.isChecked = false
            }
        }
    }

    /**
     * access the database and populates the username/password fields properly in the background
     */
    private fun populateSettings() {
        populateDisposable = Maybe
                .fromCallable<WakeOnLanSettings> {
                    DatabaseAccess.getInstance(context)
                            .serverQueries
                            .lookupById(serverId)
                            .executeAsOneOrNull()
                            ?.serverwakeonlan
                }
                .subscribeOn(Schedulers.io())
                .defaultIfEmpty(WakeOnLanSettings())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { result: WakeOnLanSettings ->
                    if (dismissed || cancelled) {
                        return@subscribe
                    }
                    settings = result
                    bindings.policy.setSelection(settings.mode.ordinal)
                    bindings.autodetect.isChecked = Compat.isArpLookupAllowed() && settings.autodetectMacAddress
                    val mac = settings.macAddress
                    bindings.macaddress.setText(mac.orEmpty())
                    var broadcast = settings.broadcastAddress
                    if (broadcast == null || settings.autodetectMacAddress) {
                        try {
                            val addy = Compat.getBroadcastAddress(context).orNull()
                            if (addy != null) {
                                broadcast = addy.hostAddress
                            }
                        } catch (e: IOException) {
                            OSLog.w(requireNotNull(e.message), e)
                        }
                    }
                    bindings.broadcast.setText(broadcast.orEmpty())
                    val ports = settings.ports.joinToString(", ")
                    bindings.ports.setText(ports)
                    resetEnabledFlags()
                }
    }

    private fun resetEnabledFlags() {
        bindings.macaddress.isEnabled = !bindings.autodetect.isChecked
    }

    private fun onResetClicked() {
        settings = WakeOnLanSettings()
        saveSettings(settings)
    }

    private fun onPositiveButtonClicked() {
        // update settings object
        settings.autodetectMacAddress = bindings.autodetect.isChecked
        settings.macAddress = bindings.macaddress.text.toString()
        if (bindings.autodetect.isChecked) {
            settings.broadcastAddress = null
        } else {
            settings.broadcastAddress = bindings.broadcast.text.toString()
        }
        val ndx = bindings.policy.selectedItemPosition
        if (ndx < 0 || ndx >= WakeOnLanSettings.Mode.values().size) { // default value
            settings.mode = WakeOnLanSettings.Mode.CONNECTION
        } else {
            settings.mode = WakeOnLanSettings.Mode.values()[bindings.policy.selectedItemPosition]
        }
        settings.ports = bindings.ports.text
                .split(",", " ", ";")
                .mapNotNull { it.toIntOrNull() }
        saveSettings(settings)
    }

    private fun saveSettings(settings: WakeOnLanSettings) { // spawn background thread to store settings to database
        OSExecutors.singleThreadScheduler().scheduleDirect {
            DatabaseAccess.getInstance(context).let { da ->
                val sq = da.serverQueries
                da.transaction {
                    sq.updateWakeOnLan(settings, serverId)
                    sq.updateServerType(ServerType.PINNED, serverId)
                }
            }
        }
    }
}