/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.databind.JsonNode;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.DeviceInterfaceInfo;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.annotation.Nonnull;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class SqueezePlayerHelper {
    private final static String INTENT_CLASS = "de.bluegaspode.squeezeplayer.playback.service.PlaybackService";
    private final static String INTENT_PACKAGE = "de.bluegaspode.squeezeplayer";

    // no instances
    private SqueezePlayerHelper() {
    }

    static public boolean isLocalSqueezePlayer(JsonNode serverStatusPlayer) {
        boolean retval = false;

        String model = serverStatusPlayer.path("model").asText();

        // older server versions report "squeezeplay", newer ones report "squeezeplayer"
        if ("squeezeplayer".equals(model) || "squeezeplay".equals(model)) {
            DeviceInterfaceInfo ii = DeviceInterfaceInfo.getInstance();
            String id = serverStatusPlayer.path("playerid").asText();
            if (id.equalsIgnoreCase(ii.mMacAddress)) {
                retval = true;
            } else {
                String address = serverStatusPlayer.path("ip").asText();
                if (address != null) {
                    int ndx = address.indexOf(':');
                    if (ndx != -1) {
                        address = address.substring(0, ndx);
                    }
                    try {
                        InetAddress spAddress = InetAddress.getByName(address);
                        for (InetAddress a : ii.mAddresses) {
                            if (a.equals(spAddress)) {
                                retval = true;
                                break;
                            }
                        }
                    } catch (UnknownHostException e) {
                        OSLog.i(Tag.DEFAULT, "Determining local squeezeplayer", e);
                    }
                }
            }
        }
        return retval;
    }

    public static boolean conditionallyStartService(Context context) {
        boolean launched = false;
        // launch squeezeplayer
        if (SBPreferences.get().isShouldAutoLaunchSqueezePlayer() && SqueezePlayerHelper.isAvailable(context)) {
            OSLog.i(Tag.DEFAULT, "SQUEEZEPLAYER CONDITIONALLY START");
            SqueezePlayerHelper.startService(context);
            launched = true;
        }
        return launched;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean startService(Context context) {
        boolean retval = false;
        Intent intent = getIntent();
        try {
            OSLog.i(Tag.DEFAULT, "SQUEEZEPLAYER START");
            ContextCompat.startForegroundService(context, intent);
            retval = true;
        } catch (Exception e) {
            // this is occasionally a securityexception
            OSLog.w(Tag.DEFAULT, "Error starting squeezeplayer", e);
        }
        return retval;
    }

    @SuppressWarnings("UnusedReturnValue")
    static public boolean stopService(Context context) {
        boolean retval = false;
        Intent intent = getIntent();
        try {
            OSLog.i(Tag.DEFAULT, "SQUEEZEPLAYER STOP");
            context.stopService(intent);
            retval = true;
        } catch (Exception e) {
            // this is occasionally a securityexception
            OSLog.w(Tag.DEFAULT, "Error stopping squeezeplayer", e);
        }
        return retval;
    }

    @SuppressWarnings("UnusedReturnValue")
    static public boolean pingService(Context context) {
        boolean retval = false;
        try {
            OSLog.i(Tag.DEFAULT, "SQUEEZEPLAYER PING");

            Intent intent = new Intent();
            intent.setClassName(INTENT_PACKAGE, INTENT_CLASS);
            context.startService(intent);
            retval = true;
        } catch (Exception e) {
            // this is occasionally a securityexception
            OSLog.w(Tag.DEFAULT, "Error pinging squeezeplayer", e);
        }
        return retval;
    }

    static public boolean isAvailable(Context context) {
        try {
            PackageManager p = context.getPackageManager();
            OSAssert.assertNotNull(p, "package manager shouldn't be null");

            p.getPackageInfo(INTENT_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            // any exception, false
            return false;
        }
    }

    @Nonnull
    static protected Intent getIntent() {
        SBContext sbContext = SBContextProvider.get();
        Intent intent = new Intent();
        intent.setClassName(INTENT_PACKAGE, INTENT_CLASS);
        intent.putExtra("intentHasServerSettings", true);
        intent.putExtra("forceSettingsFromIntent", true);

        ConnectionInfo ci = sbContext.getConnectionInfo();
        intent.putExtra("serverURL", ci.getServerHost() + ":" + ci.getServerPort());
        intent.putExtra("serverName", ci.getServerName());
        if (ci.getUsername() != null && ci.getPassword() != null) {
            intent.putExtra("username", ci.getUsername());
            intent.putExtra("password", ci.getPassword());
        }
        return intent;
    }
}
