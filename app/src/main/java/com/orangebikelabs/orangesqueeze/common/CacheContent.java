/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class CacheContent {

    public static final String COLUMN_CACHE_ID = Constants.COLUMN_ID;

    public static final String TABLE_CACHE = "cache";
    public static final String COLUMN_CACHE_KEYHASH = "cachekeyhash";
    public static final String COLUMN_CACHE_KEY = "cachekey";
    public static final String COLUMN_CACHE_ITEMSTATUS = "cacheitemstatus";
    public static final String COLUMN_CACHE_VALUE = "cachevalue";
    public static final String COLUMN_CACHE_VALUE_SIZE = "cachevaluesize";
    public static final String COLUMN_CACHE_SERVERSCAN_TIMESTAMP = "cacheserverscantimestamp";
    public static final String COLUMN_CACHE_EXPIRES_TIMESTAMP = "cacheexpirestimestamp";
    public static final String COLUMN_CACHE_LASTUSED_TIMESTAMP = "cachelastusedtimestamp";

    @Keep
    public enum ItemStatus {
        INTERNAL, EXTERNAL, INVALID, NOTFOUND;

        @Nonnull
        static public ItemStatus fromString(@Nullable String status, ItemStatus defaultValue) {
            ItemStatus retval = defaultValue;
            if (status != null) {
                try {
                    retval = ItemStatus.valueOf(status);
                } catch (IllegalStateException e) {
                    OSLog.e(e.getMessage(), e);
                }
            }
            return retval;
        }
    }
}
