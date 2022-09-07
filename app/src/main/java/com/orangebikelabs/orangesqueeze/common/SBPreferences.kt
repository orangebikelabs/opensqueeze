/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.common

import android.app.backup.BackupManager
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.text.format.DateFormat
import androidx.annotation.BoolRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.core.type.TypeReference
import com.google.common.util.concurrent.Uninterruptibles
import com.orangebikelabs.orangesqueeze.BuildConfig
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.LocaleHelper.getLanguage
import com.orangebikelabs.orangesqueeze.app.LocaleHelper.setLocale
import com.orangebikelabs.orangesqueeze.app.PhoneStateReceiver.Companion.updateComponentEnabled
import com.orangebikelabs.orangesqueeze.common.event.AppPreferenceChangeEvent
import com.orangebikelabs.orangesqueeze.compat.Compat
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.concurrent.GuardedBy
import kotlin.math.min

/**
 * Provides access to all of the preferences in Orange Squeeze.
 *
 * @author tbsandee@orangebikelabs.com
 */
class SBPreferences private constructor(private val context: Context) {

    enum class ThemePreference(val nightMode: Int) {
        @Suppress("unused")
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),

        @Suppress("unused")
        DARK(AppCompatDelegate.MODE_NIGHT_YES),
        DEFAULT(if (Build.VERSION.SDK_INT < 29) AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        companion object {
            fun lookup(value: Any?): ThemePreference {
                if (value == null) {
                    return DEFAULT
                }
                return try {
                    valueOf(value.toString().uppercase(Locale.ROOT))
                } catch (e: IllegalArgumentException) {
                    DEFAULT
                }
            }
        }

        val preferenceValue: String
            get() = name.lowercase(Locale.ROOT)
    }

    companion object {
        const val SELECTED_LANGUAGE_KEY = "SelectedLanguage"

        // this is a block of internally-used preference strings
        private const val LAST_AUTOMUTE = "LastAutomuteTime"
        private const val LAST_MUTEDPLAYERS = "LastMutedPlayers"
        private const val LAST_RUN_VERSIONCODE = "LastRunVersionCode"
        private const val LAST_CONNECTED_SQUEEZEPLAYER = "LastConnectedSqueezePlayer"
        private const val SHOWED_SQUEEZEPLAYER_STARTUP = "ShowedSqueezePlayerStartup"
        private const val GLOBALSEARCH_AUTOEXPAND = "GlobalSearchAutoExpand"
        private const val LAST_PLAYER_SLEEP_TIME = "LastPlayerSleepTime"
        private const val UUID_KEY = "UUID"
        private const val WIDGET_ENABLED_PREFIX = "WidgetEnabled_"
        private const val PREF_AUTOSCROLL_TO_CURRENT_ITEM = "AutoscrollToCurrentItem"

        /**
         * public method to trigger the initialization of the preferences object
         */
        fun asyncInitialize(context: Context) {
            val applicationContext = context.applicationContext
            OSAssert.assertNotNull(applicationContext, "application context shouldn't be null")
            OSExecutors.getUnboundedPool().execute {
                val prefs = SBPreferences(applicationContext)
                try {
                    prefs.initialize()
                } finally {
                    sPreferences.compareAndSet(null, prefs)
                    sPrefsReadyLatch.countDown()
                }
            }
        }

        private val sLock = Any()
        private val sPrefsReadyLatch = CountDownLatch(1)
        private val sPreferences = AtomicReference<SBPreferences>()

        @GuardedBy("sLock")
        private var sCheckUpgrade = false

        /**
         * retrieve the app preferences instance
         */
        @JvmStatic
        fun get(): SBPreferences {
            var prefs = sPreferences.get()
            if (prefs == null) {
                Uninterruptibles.awaitUninterruptibly(sPrefsReadyLatch)
                prefs = sPreferences.get()
            }
            return checkNotNull(prefs)
        }

        @JvmStatic
        fun getAutoDiscoveryKey(context: Context): String {
            return context.getString(R.string.pref_autodiscovery_key)
        }
    }

    private val preferenceListener = OnSharedPreferenceChangeListener { _, key ->
        val browseKey = context.getString(R.string.pref_browse_gridcellcount_key)
        val autoSizeTextKey = context.getString(R.string.pref_autosizetext_key)
        val b = BackupManager(context)
        b.dataChanged()
        var needRestart = false
        if (key == DebugLoggingPreference.DEBUG_LOGGING_EXPIRES_KEY) {
            setupLogging()
        } else if (key == browseKey) {
            needRestart = true
            setupThumbnailWidth()
        } else if (isCompactModePreference(key) || key == SELECTED_LANGUAGE_KEY || key == autoSizeTextKey) {
            needRestart = true
        } else if (isOnCallBehaviorKey(key)) {
            updateComponentEnabled(context, this@SBPreferences)
        } else if (key == SELECTED_LANGUAGE_KEY) {
            needRestart = true
        }
        BusProvider.getInstance().post(AppPreferenceChangeEvent(key, needRestart))
    }

    /**
     * the actual shared preferences object used (and exposed) by this wrapper class
     */
    private val internalPref: SharedPreferences

    @Volatile
    var gridThumbnailWidth = 0
        private set

    @Volatile
    var gridCellWidth = 0
        private set

    @Volatile
    var gridIconPadding = 0
        private set

    @Volatile
    var gridCellSpacing = 0
        private set

    init {
        OSAssert.assertApplicationContext(context)
        internalPref = PreferenceManager.getDefaultSharedPreferences(context)
        internalPref.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun initialize() {
        upgradePreferences()
        setupLogging()
        val themeString = internalPref.getString(themeIdKey, ThemePreference.DEFAULT.toString())
        val theme = ThemePreference.lookup(themeString)
        AppCompatDelegate.setDefaultNightMode(theme.nightMode)

        setupThumbnailWidth()
    }

    fun shouldAutoscrollToCurrentItem(): Boolean {
        return getBoolean(PREF_AUTOSCROLL_TO_CURRENT_ITEM, true)
    }

    fun setShouldAutoscrollToCurrentItem(value: Boolean) {
        setBoolean(PREF_AUTOSCROLL_TO_CURRENT_ITEM, value)
    }

    fun isCompactModePreference(key: String): Boolean {
        return compactModeKey == key
    }

    var downloadLocation: File
        get() {
            val key = getString(R.string.pref_trackdownload_location_key, R.string.default_pref_trackdownload_location)
            return if (key.isBlank()) {
                defaultDownloadLocation
            } else {
                File(key)
            }
        }
        set(file) {
            setString(R.string.pref_trackdownload_location_key, file.absolutePath)
        }

    val defaultDownloadLocation: File
        get() = Compat.getPublicMediaDirs()[0]

    val uUID: String
        get() {
            synchronized(sLock) {
                var key = getString(UUID_KEY)
                if (key == null) {
                    key = UUID.randomUUID().toString()
                            .filter { Character.isLetterOrDigit(it) }
                    setString(UUID_KEY, key)
                }
                return key
            }
        }

    var selectedLanguage: String
        get() = getLanguage(context)
        set(language) {
            setLocale(context, language)
        }

    fun shouldAutoSizeText(): Boolean {
        return getBoolean(R.string.pref_autosizetext_key, R.bool.default_pref_autosizetext)
    }

    fun shouldShowNowPlayingNotification(): Boolean {
        return getBoolean(R.string.pref_shownowplayingnotification_key, R.bool.default_pref_shownowplayingnotification)
    }

    fun getLastPlayerSleepTime(units: TimeUnit): Long {
        val value = getIntegerWithDefaultValue(LAST_PLAYER_SLEEP_TIME, Constants.DEFAULT_PLAYER_SLEEP_TIME)
        return units.convert(value.toLong(), TimeUnit.SECONDS)
    }

    fun setLastPlayerSleepTime(value: Long, units: TimeUnit) {
        val edit = internalPref.edit()
        edit.putInt(LAST_PLAYER_SLEEP_TIME, units.toSeconds(value).toInt())
        edit.apply()
    }

    private var lastRunVersion: Int
        get() = getIntegerWithDefaultValue(LAST_RUN_VERSIONCODE, 0)
        private set(versionCode) {
            if (internalPref.getInt(LAST_RUN_VERSIONCODE, 0) != versionCode) {
                val edit = internalPref.edit()
                edit.putInt(LAST_RUN_VERSIONCODE, versionCode)
                edit.apply()
            }
        }

    val lastConnectedSqueezePlayerId: PlayerId?
        get() {
            val id = getString(LAST_CONNECTED_SQUEEZEPLAYER)
            return id?.let { PlayerId(it) }
        }

    fun setLastConnectedSqueezePlayerId(lastConnectedSqueezePlayer: PlayerId) {
        setString(LAST_CONNECTED_SQUEEZEPLAYER, lastConnectedSqueezePlayer.toString())
    }

    val isFirstLaunch: Boolean
        get() = lastRunVersion == 0// otherwise, definitely not an upgrade// last run code is wrong, it's an upgrade

    // not upgrade if it's the first launch
    val shouldUpgradeFirstLaunch: Boolean
        get() {
            return if (isFirstLaunch) {
                // not upgrade if it's the first launch
                false
            } else BuildConfig.VERSION_CODE != lastRunVersion
        }

    fun updateLastRunVersionCode() {
        val currentVersionCode = BuildConfig.VERSION_CODE
        lastRunVersion = currentVersionCode
    }

    val trackSelectPlayMode: PlayOptions
        get() = PlayOptions.fromValue(getString(R.string.pref_trackselect_playmode_key, R.string.default_pref_trackselect_playmode))

    var isShowedSqueezePlayerStartupOption: Boolean
        get() = getBoolean(SHOWED_SQUEEZEPLAYER_STARTUP, false)
        set(value) {
            setBoolean(SHOWED_SQUEEZEPLAYER_STARTUP, value)
        }

    var isShouldAutoLaunchSqueezePlayer: Boolean
        get() = getBoolean(R.string.pref_autolaunch_squeezeplayer_key, R.bool.default_pref_autolaunch_squeezeplayer)
        set(value) {
            setBoolean(R.string.pref_autolaunch_squeezeplayer_key, value)
        }

    val isKeepScreenOnEnabled: Boolean
        get() = getBoolean(R.string.pref_keepscreenon_key, R.bool.default_pref_keepscreenon)

    val isArtistArtworkDisabled: Boolean
        get() = getBoolean(R.string.pref_browse_disableartistartwork_key, R.bool.default_pref_browse_disableartistartwork)

    val keepScreenOnKey: String
        get() = context.getString(R.string.pref_keepscreenon_key)

    val compactModeKey: String
        get() = context.getString(R.string.pref_compactmode_key)

    val isBrowseActionBarEnabled: Boolean
        get() = getBoolean(R.string.pref_browse_showactionbar_key, R.bool.default_pref_browse_showactionbar)

    val isBrowseNowPlayingBarEnabled: Boolean
        get() = getBoolean(R.string.pref_browse_shownowplaying_key, R.bool.default_pref_browse_shownowplaying)

    fun shouldUse24HourTimeFormat(): Boolean {
        return getBoolean(R.string.pref_use24hourtimeformat_key, DateFormat.is24HourFormat(context))
    }

    val isAutoConnectEnabled: Boolean
        get() = getBoolean(R.string.pref_autoconnect_key, R.bool.default_pref_autoconnect)

    val cacheLocationKey: String
        get() = context.getString(R.string.pref_cachelocation_key)

    val cacheLocation: CacheLocation
        get() {
            val defaultValue = context.getString(R.string.default_pref_cachelocation)
            val value = getString(cacheLocationKey, defaultValue)
            return CacheLocation.fromKey(value)
        }

    val cacheStorageSizeKey: String
        get() = context.getString(R.string.pref_cache_storagesize_key)

    val cacheStorageSize: Int
        get() {
            val defaultValue = context.getString(R.string.default_pref_cache_storagesize)
            return getString(cacheStorageSizeKey, defaultValue).toIntOrNull()
                    ?: defaultValue.toInt()
        }

    fun shouldUseVolumeIntegration(): Boolean {
        return getBoolean(R.string.pref_systemvolumecontrol_key, R.bool.default_pref_systemvolumecontrol)
    }

    fun shouldEmitSilentAudio(): Boolean {
        return getBoolean(R.string.pref_silentaudiohack_key, R.bool.default_pref_silentaudiohack)
    }

    fun shouldPauseOnHeadphoneDisconnect(): Boolean {
        return shouldEmitSilentAudio() && getBoolean(R.string.pref_pauseonheadphonedisconnect_key, R.bool.default_pref_pauseonheadphonedisconnect)
    }

    val isAutoDiscoverEnabled: Boolean
        get() = getBoolean(R.string.pref_autodiscovery_key, R.bool.default_pref_autodiscovery)

    fun isOnCallBehaviorKey(key: String): Boolean {
        return context.getString(R.string.pref_automaticmute_key) == key
    }

    // pre 1.2.2 preference format
    var onCallBehavior: OnCallMuteBehavior
        get() {
            val key = context.getString(R.string.pref_automaticmute_key)
            val defaultValue = context.getString(R.string.default_pref_automaticmute_behavior)
            return try {
                val value = checkNotNull(internalPref.getString(key, defaultValue))
                OnCallMuteBehavior.fromValue(value)
            } catch (e: ClassCastException) {
                // pre 1.2.2 preference format
                if (internalPref.getBoolean(key, false)) {
                    OnCallMuteBehavior.MUTE
                } else {
                    OnCallMuteBehavior.NOTHING
                }
            }
        }
        set(behavior) {
            val key = context.getString(R.string.pref_automaticmute_key)
            setString(key, behavior.value)
        }

    fun setAutoDiscover(enabled: Boolean) {
        setBoolean(R.string.pref_autodiscovery_key, enabled)
    }

    /**
     * returns null to indicate no automatic unmute
     */
    fun getAutoUnmute(units: TimeUnit): Long? {
        var retval: Long? = null
        try {
            val value = getString(R.string.pref_automaticunmute_key, R.string.default_pref_automaticunmute)

            // use seconds because TimeUnit.MINUTES is missing on some earlier platforms
            val valInMinutes = value.toLong()
            if (valInMinutes == -1L) {
                // absent
            } else if (valInMinutes == -2L) {
                retval = units.convert(24 * 60 * 60.toLong(), TimeUnit.SECONDS)
            } else {
                val valInSeconds = valInMinutes * 60
                retval = units.convert(valInSeconds, TimeUnit.SECONDS)
            }
        } catch (e: NumberFormatException) {
            Reporting.report(e)
        }
        return retval
    }

    fun setLastMutedEvent(muteTimeInMillis: Long, players: List<PlayerId>) {
        val pref = players.joinToString(",")
        val edit = internalPref.edit()
        edit.putLong(LAST_AUTOMUTE, muteTimeInMillis)
        edit.putString(LAST_MUTEDPLAYERS, pref)
        edit.apply()
    }

    val lastMutedPlayers: List<PlayerId>
        get() {
            val pref = getString(LAST_MUTEDPLAYERS)
            if (pref.isNullOrBlank()) {
                return emptyList()
            }
            return pref.split(",").map { PlayerId(it) }
        }

    val lastMutedTime: Long
        get() = getLongWithDefaultValue(LAST_AUTOMUTE, 0L)

    val isCompactMode: Boolean
        get() = getBoolean(R.string.pref_compactmode_key, R.bool.default_pref_compactmode)

    /* no overrides */
    var globalSearchAutoExpandSet: Set<String>
        get() {
            var retval = emptySet<String>()
            val value = getString(GLOBALSEARCH_AUTOEXPAND)
            if (value != null) {
                try {
                    val reader = JsonHelper.getJsonObjectReader().forType(object : TypeReference<Set<String?>?>() { /* no overrides */ })
                    retval = reader.readValue(value)
                } catch (e: IOException) {
                    Reporting.report(e, "Error loading global search pref", value)
                }
            }
            return retval
        }
        set(autoExpandSet) {
            try {
                val writer = JsonHelper.getJsonObjectWriter()
                val value = writer.writeValueAsString(autoExpandSet)
                setString(GLOBALSEARCH_AUTOEXPAND, value)
            } catch (e: IOException) {
                Reporting.report(e, "Error persisting global search pref", autoExpandSet)
            }
        }

    fun setWidgetEnabled(simpleName: String, enabled: Boolean) {
        setBoolean(WIDGET_ENABLED_PREFIX + simpleName, enabled)
    }

    fun isWidgetEnabled(simpleName: String): Boolean {
        return getBoolean(WIDGET_ENABLED_PREFIX + simpleName, false)
    }

    /**
     * return list of keys that affect the track download results
     */
    val downloadResultsKeys: List<String>
        get() = listOf(context.getString(R.string.pref_trackdownload_transcodeenabled_key),
                context.getString(R.string.pref_trackdownload_location_key), context.getString(R.string.pref_trackdownload_transcodeformat_key))

    val themeIdKey: String
        get() = context.getString(R.string.pref_theme_key)

    val isTranscodingEnabled: Boolean
        get() = getBoolean(R.string.pref_trackdownload_transcodeenabled_key, R.bool.default_pref_trackdownload_transcodeenabled)

    val transcodeFormat: String
        get() {
            return getString(R.string.pref_trackdownload_transcodeformat_key, R.string.default_pref_trackdownload_transcodeformat).trim().lowercase(Locale.ROOT)
        }

    val transcodeOptions: List<String>
        get() {
            val options = getString(R.string.pref_trackdownload_transcodeoptions_key, R.string.default_pref_trackdownload_transcodeoptions)
            return options.split(",").map { it.trim() }
        }

    private fun setBoolean(@StringRes keyRid: Int, value: Boolean) {
        val key = context.getString(keyRid)
        setBoolean(key, value)
    }

    private fun setBoolean(key: String, value: Boolean) {
        val edit = internalPref.edit()
        edit.putBoolean(key, value)
        edit.apply()
    }

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            internalPref.getBoolean(key, defaultValue)
        } catch (e: Exception) {
            Reporting.report(e)
            defaultValue
        }
    }

    private fun getBoolean(key: String, @BoolRes defaultRid: Int): Boolean {
        val defValue = context.resources.getBoolean(defaultRid)
        return getBoolean(key, defValue)
    }

    private fun getBoolean(@StringRes keyRid: Int, @BoolRes defaultRid: Int): Boolean {
        val key = context.getString(keyRid)
        return getBoolean(key, defaultRid)
    }

    private fun getBoolean(@StringRes keyRid: Int, defaultValue: Boolean): Boolean {
        val key = context.getString(keyRid)
        return getBoolean(key, defaultValue)
    }

    private fun getLongWithDefaultValue(key: String, defaultValue: Long): Long {
        return try {
            internalPref.getLong(key, defaultValue)
        } catch (e: Exception) {
            Reporting.report(e)
            defaultValue
        }
    }

    private fun getIntegerWithDefaultValue(key: String, defaultValue: Int): Int {
        return try {
            internalPref.getInt(key, defaultValue)
        } catch (e: Exception) {
            Reporting.report(e)
            defaultValue
        }
    }

    /**
     * @return null if the key is not defined
     */
    private fun getString(key: String): String? {
        return try {
            internalPref.getString(key, null)
        } catch (e: Exception) {
            Reporting.report(e)
            null
        }
    }

    private fun getString(key: String, defValue: String): String {
        return try {
            checkNotNull(internalPref.getString(key, defValue))
        } catch (e: Exception) {
            Reporting.report(e)
            defValue
        }
    }

    private fun getString(@StringRes keyRid: Int, @StringRes defaultRid: Int): String {
        val key = context.getString(keyRid)
        val defValue = context.resources.getString(defaultRid)
        return getString(key, defValue)
    }

    private fun setString(@StringRes keyRid: Int, value: String) {
        val key = context.getString(keyRid)
        setString(key, value)
    }

    private fun setString(key: String, value: String?) {
        val edit = internalPref.edit()
        edit.putString(key, value)
        edit.apply()
    }

    private fun upgradePreferences() {
        synchronized(sLock) {
            if (!sCheckUpgrade) {
                sCheckUpgrade = true

                // remove memory size preference (obsolete)
                val edit = internalPref.edit()
                val k = context.getString(R.string.pref_automaticmute_key)
                try {
                    // if this works, we're good
                    internalPref.getString(k, "")
                } catch (e: ClassCastException) {
                    OSLog.i("Upgrading on-call behavior preferences")
                    val value = internalPref.getBoolean(k, false)
                    edit.remove(k)
                    edit.putString(k, if (value) OnCallMuteBehavior.MUTE.value else OnCallMuteBehavior.NOTHING.value)
                }
                edit.remove(context.getString(R.string.removed_pref_cache_memorysize_key))
                edit.remove(context.getString(R.string.removed_pref_artworkprefetch_key))
                edit.remove(context.getString(R.string.removed_pref_browsealbumsort))

                // migrate old theme keys to new keys
                val oldThemeKey = context.getString(R.string.removed_pref_themeid)
                val oldTheme = internalPref.getString(oldThemeKey, null)
                if (oldTheme != null) {
                    if (oldTheme.contains("DARK")) {
                        edit.putString(themeIdKey, ThemePreference.DARK.preferenceValue)
                        AppCompatDelegate.setDefaultNightMode(ThemePreference.DARK.nightMode)
                    } else {
                        edit.putString(themeIdKey, ThemePreference.DEFAULT.preferenceValue)
                        AppCompatDelegate.setDefaultNightMode(ThemePreference.DEFAULT.nightMode)
                    }
                    if (oldTheme.contains("COMPACT")) {
                        edit.putBoolean(context.getString(R.string.pref_compactmode_key), true)
                    }
                    edit.remove(oldThemeKey)
                }
                edit.apply()
            }
        }
        if (isFirstLaunch || shouldUpgradeFirstLaunch) {
            updateComponentEnabled(context, this@SBPreferences)
        }
    }

    /**
     * initializes various static variables for the logging
     */
    private fun setupLogging() {
        val value = internalPref.getLong(DebugLoggingPreference.DEBUG_LOGGING_EXPIRES_KEY, 0L)
        if (value != 0L && System.currentTimeMillis() < value) {
            OSLog.enableDebugLogging()
        }
    }


    // list mode by default
    // automatic calculation
    val browseGridCount: Int
        get() {
            val value = getString(R.string.pref_browse_gridcellcount_key, R.string.default_pref_browse_gridcellcount)
            var intValue = value.toIntOrNull() ?: -1
            // automatic calculation
            if (intValue < 0) {
                intValue = if (context.resources.getBoolean(R.bool.browse_grid_default)) {
                    val dm = context.resources.displayMetrics
                    val minWidth = min(dm.heightPixels, dm.widthPixels)
                    val targetCellWidth = context.resources.getDimensionPixelSize(R.dimen.target_grid_item_dimension)
                    minWidth / targetCellWidth
                } else {
                    // list mode by default
                    0
                }
            }
            if (intValue >= 6) {
                intValue = 6
            }
            return intValue
        }

    private fun setupThumbnailWidth() {
        val gridCount = browseGridCount
        if (gridCount == 0) {
            gridCellWidth = 0
            gridThumbnailWidth = 0
            gridCellSpacing = 0
            gridIconPadding = 0
            return
        }
        val dm = context.resources.displayMetrics
        val actualWidth = min(dm.heightPixels, dm.widthPixels) - context.resources.getDimensionPixelOffset(R.dimen.platform_scrollbar_size)
        gridCellSpacing = (dm.density * 4).toInt()

        // icon padding is zero
        gridIconPadding = 0
        val outCellPadding = context.resources.getDimensionPixelOffset(R.dimen.gridview_left_padding) +
                context.resources.getDimensionPixelOffset(R.dimen.gridview_right_padding) + (gridCount - 1) * gridCellSpacing
        val inCellPadding = gridCount * 2 * gridIconPadding
        gridThumbnailWidth = (actualWidth - inCellPadding - outCellPadding) / gridCount
        gridCellWidth = (actualWidth - outCellPadding) / gridCount
    }
}