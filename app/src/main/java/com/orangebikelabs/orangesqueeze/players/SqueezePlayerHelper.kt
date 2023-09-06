/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.players

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.databind.JsonNode
import com.orangebikelabs.orangesqueeze.common.DeviceInterfaceInfo
import com.orangebikelabs.orangesqueeze.common.OSLog
import com.orangebikelabs.orangesqueeze.common.SBContextProvider
import com.orangebikelabs.orangesqueeze.common.SBPreferences
import com.orangebikelabs.orangesqueeze.common.isAppInstalled
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * @author tbsandee@orangebikelabs.com
 */
object SqueezePlayerHelper {
    private const val INTENT_CLASS = "de.bluegaspode.squeezeplayer.playback.service.PlaybackService"
    private const val INTENT_PACKAGE = "de.bluegaspode.squeezeplayer"

    @JvmStatic
    fun isLocalSqueezePlayer(serverStatusPlayer: JsonNode): Boolean {
        var retval = false
        val model = serverStatusPlayer.path("model").asText()

        // older server versions report "squeezeplay", newer ones report "squeezeplayer"
        if ("squeezeplayer" == model || "squeezeplay" == model) {
            val ii = DeviceInterfaceInfo.getInstance()
            val id = serverStatusPlayer.path("playerid").asText()
            if (id.equals(ii.mMacAddress, ignoreCase = true)) {
                retval = true
            } else {
                var address = serverStatusPlayer.path("ip").asText()
                if (address != null) {
                    val ndx = address.indexOf(':')
                    if (ndx != -1) {
                        address = address.substring(0, ndx)
                    }
                    try {
                        val spAddress = InetAddress.getByName(address)
                        for (a in ii.mAddresses) {
                            if (a == spAddress) {
                                retval = true
                                break
                            }
                        }
                    } catch (e: UnknownHostException) {
                        OSLog.i(OSLog.Tag.DEFAULT, "Determining local squeezeplayer", e)
                    }
                }
            }
        }
        return retval
    }

    @JvmStatic
    fun conditionallyStartService(context: Context): Boolean {
        // launch squeezeplayer
        return if (SBPreferences.get().isShouldAutoLaunchSqueezePlayer && isAvailable(context)) {
            OSLog.i(OSLog.Tag.DEFAULT, "SQUEEZEPLAYER CONDITIONALLY START")
            startService(context)
            true
        } else {
            false
        }
    }

    @JvmStatic
    fun startService(context: Context): Boolean {
        val intent = buildIntent()
        return try {
            OSLog.i(OSLog.Tag.DEFAULT, "SQUEEZEPLAYER START")
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            // this is occasionally a securityexception
            OSLog.w(OSLog.Tag.DEFAULT, "Error starting squeezeplayer", e)
            false
        }
    }

    @JvmStatic
    fun stopService(context: Context): Boolean {
        val intent = buildIntent()
        return try {
            OSLog.i(OSLog.Tag.DEFAULT, "SQUEEZEPLAYER STOP")
            context.stopService(intent)
            true
        } catch (e: Exception) {
            // this is occasionally a securityexception
            OSLog.w(OSLog.Tag.DEFAULT, "Error stopping squeezeplayer", e)
            false
        }
    }

    @JvmStatic
    fun pingService(context: Context): Boolean {

        return try {
            OSLog.i(OSLog.Tag.DEFAULT, "SQUEEZEPLAYER PING")
            val intent = Intent()
            intent.setClassName(INTENT_PACKAGE, INTENT_CLASS)
            context.startService(intent)
            true
        } catch (e: Exception) {
            // this is occasionally a securityexception
            OSLog.w(OSLog.Tag.DEFAULT, "Error pinging squeezeplayer", e)
            false
        }
    }

    @JvmStatic
    fun isAvailable(context: Context): Boolean {
        return context.isAppInstalled(INTENT_PACKAGE)
    }

    private fun buildIntent(): Intent {
        return Intent().apply {
            setClassName(INTENT_PACKAGE, INTENT_CLASS)
            putExtra("intentHasServerSettings", true)
            putExtra("forceSettingsFromIntent", true)
            val ci = SBContextProvider.get().connectionInfo
            putExtra("serverURL", ci.serverHost + ":" + ci.serverPort)
            putExtra("serverName", ci.serverName)
            if (ci.username != null && ci.password != null) {
                putExtra("username", ci.username)
                putExtra("password", ci.password)
            }
        }
    }
}