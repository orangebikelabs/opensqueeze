/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.BuildConfig;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Various constants used by the app.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class Constants {
    final public static String SQUEEZENETWORK_HOSTNAME = "www.squeezenetwork.com";
    final public static String SQUEEZENETWORK_CONNECT_COMMAND_HOSTNAME = "jive.squeezenetwork.com";
    final public static String SQUEEZENETWORK_SERVERNAME = "mysqueezebox.com";
    final public static List<String> SQUEEZENETWORK_DOMAINS = ImmutableList.of("squeezenetwork.com", "mysqueezebox.com");

    final public static int DEFAULT_SERVER_PORT = 9000;
    final public static int SQUEEZENETWORK_PORT = 80;

    final public static TimeUnit TIME_UNITS = TimeUnit.SECONDS;

    final public static int SQUEEZEPLAYER_PING_INTERVAL = 600; // 10 minutes
    final public static int DISCOVERY_PACKET_INTERVAL = 600; // 10 minutes
    final public static int CACHEMAINTENANCE_INTERVAL = 300; // 5 minute
    final public static int CACHEMAINTENANCE_DELAY = 15; // 15 seconds
    final public static int CONNECTION_TIMEOUT = 60;
    final public static int READ_TIMEOUT = 60;
    final public static int WRITE_TIMEOUT = 60;

    final public static int DEFAULT_PLAYER_SLEEP_TIME = 3600;

    public static final String COLUMN_ID = "_id";

    public static final int NOTIFICATIONID_DOWNLOAD = 0;
    public static final int NOTIFICATIONID_CACHEWIPE = 1;

    public static final String FILEPROVIDER_AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider";

    final public static int KB = 1000;
    final public static int MB = KB * KB;
    final public static int MiB = 1024 * 1024;
}
