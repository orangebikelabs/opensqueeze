/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.preference.CheckBoxPreference;

/**
 * Custom preference that manages the debug logging preference, which is expanded to automatically disable itself after 24 hours.
 *
 * @author tsandee
 */
public class DebugLoggingPreference extends CheckBoxPreference {
    /**
     * used to be called Verbose logging, keep the pref key the same
     */
    final public static String DEBUG_LOGGING_EXPIRES_KEY = "VerboseLoggingExpires";

    public DebugLoggingPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public DebugLoggingPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DebugLoggingPreference(Context context) {
        super(context);
    }

    @Override
    public void setChecked(boolean checked) {
        long expire = checked ? (System.currentTimeMillis() + (86400L * 1000L)) : 0L;
        setChecked(checked, expire);
    }

    public void setChecked(boolean checked, long expireTime) {
        super.setChecked(checked);

        SharedPreferences prefs = getSharedPreferences();
        SharedPreferences.Editor editor = prefs.edit();
        if (editor == null) {
            return;
        }

        editor.putLong(DEBUG_LOGGING_EXPIRES_KEY, expireTime);
        editor.apply();
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        SharedPreferences prefs = getSharedPreferences();
        long expires = 0L;

        if (prefs != null) {
            expires = prefs.getLong(DEBUG_LOGGING_EXPIRES_KEY, 0L);
        }

        // bump expire time by 1ms to make sure that updated value takes affect
        setChecked(expires > System.currentTimeMillis(), expires + 1L);
    }
}