/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.*
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.content.ContextCompat
import com.gojuno.koptional.None
import com.gojuno.koptional.Optional
import com.gojuno.koptional.Some
import com.gojuno.koptional.toOptional
import com.google.common.base.MoreObjects
import com.orangebikelabs.orangesqueeze.appwidget.WidgetCommon
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.common.PlayerStatus.PlayerButton
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState
import com.orangebikelabs.orangesqueeze.net.DeviceConnectivity
import com.squareup.otto.Subscribe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit


/**
 * Class is final to ensure @Subscribe events aren't lost.
 *
 * @author tsandee
 */

class ServerConnectionService : Service() {

    companion object {
        private const val base = "com.orangebikelabs.orangesqueeze.app.ServerConnectionService"

        const val EXTRA_COMMANDS = "commands"
        const val EXTRA_PLAYER = "player"

        private const val SERVICE_TIMEOUT_IN_MILLIS = 120_000L

        // accessed from main thread only
        private var _serviceMayBeStarted = false

        // written from main thread, read from anywhere
        @Volatile
        private var _serviceWasStopped = false

        /**
         * retrieve an intent targeting this service. sets the service key automatically
         */
        fun getIntent(context: Context, action: ServiceActions): Intent {
            val newIntent = Intent(context, ServerConnectionService::class.java)
            newIntent.action = action.intentAction
            return newIntent
        }

        /**
         * retrieve an intent targeting this service. sets the service key automatically
         */
        fun getBroadcastIntent(action: BroadcastServiceActions): Intent {
            val newIntent = Intent()
            newIntent.action = action.intentAction
            return newIntent
        }

        /**
         * return whether or not the service might be started -- it could have been killed, but if false is returned then it is absolutely NOT running
         */
        var serviceMayBeStarted: Boolean
            private set(value) {
                OSAssert.assertMainThread()
                _serviceMayBeStarted = value
            }
            get() {
                OSAssert.assertMainThread()
                return _serviceMayBeStarted
            }

        var serviceWasStopped: Boolean
            private set(value) {
                OSAssert.assertMainThread()
                _serviceWasStopped = value
            }
            get() {
                // variable is volatile, it's ok to read blindly
                return _serviceWasStopped
            }
    }

    enum class ServiceActions {
        SEND_COMMANDS, THUMBSUP, THUMBSDOWN, BIND_TO_SERVICE, SERVICE_TO_BACKGROUND, SERVICE_TO_FOREGROUND;

        companion object {
            fun fromIntentAction(action: String?): ServiceActions? {
                if (action == null) return null

                if (!action.startsWith("$base.")) {
                    return null
                }
                val trimmedActionString = action.substring(base.length + 1)
                return values().firstOrNull { it.name == trimmedActionString }
            }
        }

        val intentAction: String
            get() = "$base.$name"

    }

    enum class BroadcastServiceActions {
        UPDATE_WIDGETS, STOP_SERVICE;

        val intentAction: String
            get() = "$base.$name"
    }

    enum class EligibleForShutdown {
        NO, IFSTOPPED, IFPAUSED
    }

    private var mediaSessionHelper: MediaSessionHelper? = null

    // accessed by main thread only
    private var broadcastReceiver: BroadcastReceiver? = null

    private var eligibleForShutdown: EligibleForShutdown = EligibleForShutdown.NO

    private var idleMode = false
    private var isCharging = false

    private var lastWasPausedWithUi = false

    // accessed by main thread only
    private var uiRefCount = 0

    private val uiGone
        get() = uiRefCount == 0

    // accessed by main thread only
    private var powerManager: PowerManager? = null

    private var pendingShutdownDisposable: Disposable? = null

    private val playerStatusProcessor: PublishProcessor<Optional<PlayerStatus>> = PublishProcessor.create()

    private val compositeDisposable = CompositeDisposable()

    private var needsForegroundPromotion = false

    // Binder given to clients
    private val binder = LocalBinder()

    private data class StatusUpdateStructure(val needsForegroundPromotion: Boolean, val status: PlayerStatus? = null, val sessionToken: MediaSessionCompat.Token? = null)

    override fun onCreate() {
        super.onCreate()

        OSLog.i("ServerConnectionService::onCreate")

        serviceMayBeStarted = true
        serviceWasStopped = false

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager?

        val filter = IntentFilter()
        @Suppress("DEPRECATION")
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= 23) {
            filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        BroadcastServiceActions.values().forEach {
            filter.addAction(it.intentAction)
        }
        broadcastReceiver = RemoteBroadcastReceiver()

        registerReceiver(broadcastReceiver, filter)

        // allow service to time out automatically if nothing intervenes
        eligibleForShutdown = EligibleForShutdown.IFSTOPPED

        BusProvider.getInstance().register(this)

        val context = SBContextProvider.initializeAndGet(this)
        context.onServiceCreate(this)

        mediaSessionHelper = MediaSessionHelper.newInstance(this.applicationContext).also {
            it.register()
        }

        // handle widget updates
        compositeDisposable += playerStatusProcessor
                .onBackpressureLatest()
                // short circuit if service is stopped
                .filter { !serviceWasStopped }
                .observeOn(OSExecutors.singleThreadScheduler())
                .onBackpressureLatest()
                .subscribe { status ->
                    WidgetCommon.updateWidgetsWithPlayerStatus(this@ServerConnectionService, status.toNullable())
                }

        // handle notification updates
        compositeDisposable += playerStatusProcessor
                .onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .map { status ->
                    OSAssert.assertMainThread()
                    // short circuit this to show dummy notification if necessary
                    if (serviceWasStopped || !SBPreferences.get().shouldShowNowPlayingNotification()) {
                        return@map StatusUpdateStructure(needsForegroundPromotion = needsForegroundPromotion, sessionToken = mediaSessionHelper?.token)
                    }

                    val savedLastWasPausedWithUi = lastWasPausedWithUi
                    lastWasPausedWithUi = false

                    val showNotificationStatus: PlayerStatus?
                    when (status) {
                        is Some -> {
                            val playerMode = status.value.mode
                            if (uiGone) {
                                // only show notifications if the app is not in the foreground and player paused or playing
                                if (playerMode == PlayerStatus.Mode.PAUSED && savedLastWasPausedWithUi) {
                                    // very specific case, we don't show notification and allow service to stop
                                    eligibleForShutdown = EligibleForShutdown.IFPAUSED
                                    showNotificationStatus = null
                                } else if (playerMode == PlayerStatus.Mode.PLAYING || (playerMode == PlayerStatus.Mode.PAUSED && eligibleForShutdown != EligibleForShutdown.IFPAUSED)) {
                                    eligibleForShutdown = EligibleForShutdown.IFSTOPPED
                                    showNotificationStatus = status.value
                                } else {
                                    showNotificationStatus = null
                                }
                            } else {
                                // under certain conditions, save this condition
                                if (playerMode == PlayerStatus.Mode.PAUSED) {
                                    lastWasPausedWithUi = true
                                }
                                showNotificationStatus = null
                            }
                        }
                        is None -> {
                            showNotificationStatus = null
                        }
                    }
                    updateServiceShutdownTimeout(isPlayerStatusUpdate = true)
                    StatusUpdateStructure(status = showNotificationStatus, sessionToken = mediaSessionHelper?.token, needsForegroundPromotion = needsForegroundPromotion)
                }
                .observeOn(OSExecutors.singleThreadScheduler())
                .onBackpressureBuffer()
                .map { sus ->
                    if (sus.status != null) {
                        NotificationCommon.showNowPlayingNotification(this, sus.status, sus.sessionToken)
                        true
                    } else if (sus.needsForegroundPromotion) {
                        // FIXME this may not be necessary
                        // NotificationCommon.showEmptyNotification(this)
                        true
                    } else {
                        NotificationCommon.cancelNowPlayingNotification(this)
                        false
                    }
                }
                .subscribe { resetNeedsForegroundPromotion ->
                    if (resetNeedsForegroundPromotion) {
                        AndroidSchedulers.mainThread().scheduleDirect {
                            needsForegroundPromotion = false
                        }
                    }
                }

        // fire initial values
        fireCurrentPlayerStatus()
    }

    override fun onDestroy() {
        super.onDestroy()

        OSLog.i("ServerConnectionService::onDestroy")

        clearShutdownTimeout()

        NotificationCommon.cancelNowPlayingNotification(this)

        compositeDisposable.clear()

        broadcastReceiver?.let {
            unregisterReceiver(it)
            broadcastReceiver = null
        }

        serviceMayBeStarted = false

        // unregister receiver first to avoid race conditions relating to messages generated by calling onStop()
        BusProvider.getInstance().unregister(this)

        SBContextProvider.get().onServiceDestroy(this)

        mediaSessionHelper?.unregister()
        mediaSessionHelper = null

        // reset this
        SBActivity.sShouldAutoStart = true

        powerManager = null

        // without this, widget will display out-of-date info forever, misleading user
        val ac = applicationContext
        OSExecutors.getUnboundedPool().execute {
            WidgetCommon.setDisconnectedWidgets(ac)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }
        if (intent.action == "android.intent.action.MEDIA_BUTTON") {
            mediaSessionHelper?.handleMediaButtonIntent(intent)
            return START_NOT_STICKY
        }

        val action = ServiceActions.fromIntentAction(intent.action)

        var runnable: Runnable? = null

        when (action) {
            ServiceActions.SEND_COMMANDS -> {
                // these are arbitrary commands targeting either the current or a specific player
                val commands = checkNotNull(intent.getStringArrayExtra(EXTRA_COMMANDS))
                val playerId = PlayerId.newInstance(intent.getStringExtra(EXTRA_PLAYER))

                runnable = ConnectionAwareRunnable(location = "SEND_COMMANDS") { context ->
                    if (playerId != null) {
                        context.sendPlayerCommand(playerId, listOf(*commands))
                    } else {
                        context.newRequest(*commands).submit(OSExecutors.getCommandExecutor())
                    }
                }
            }
            ServiceActions.THUMBSUP -> {
                // special 'thumbs up' event
                runnable = ConnectionAwareRunnable(location = "THUMBSUP") { context ->
                    try {
                        val status = context.checkedPlayerStatus
                        val buttonStatus = status.getButtonStatus(PlayerButton.THUMBSUP).orNull()
                        if (buttonStatus != null && buttonStatus.commands.isNotEmpty()) {
                            context.sendPlayerCommand(buttonStatus.commands)
                            buttonStatus.markPressed(status)
                        }
                    } catch (e: PlayerNotFoundException) {
                        // ignore
                    }
                }
            }
            ServiceActions.THUMBSDOWN -> {
                // special 'thumbs down' event
                runnable = ConnectionAwareRunnable(location = "THUMBSDOWN") { context ->
                    try {
                        val status = context.checkedPlayerStatus
                        val buttonStatus = status.getButtonStatus(PlayerButton.THUMBSDOWN).orNull()
                        if (buttonStatus != null && buttonStatus.commands.isNotEmpty()) {
                            context.sendPlayerCommand(buttonStatus.commands)
                            buttonStatus.markPressed(status)
                        }
                    } catch (e: PlayerNotFoundException) {
                        // ignore
                    }

                }
            }
            ServiceActions.BIND_TO_SERVICE -> {
                OSLog.v("Service bound from app UI")
                serviceWasStopped = false
            }
            ServiceActions.SERVICE_TO_BACKGROUND -> {
                OSLog.v("Service started for background operation")
                serviceWasStopped = false

                SBContextProvider.get().startAutoConnect()
            }
            ServiceActions.SERVICE_TO_FOREGROUND -> {
                OSLog.v("Service promoted to foreground operation")
                serviceWasStopped = false
                needsForegroundPromotion = true

                SBContextProvider.get().startAutoConnect()
            }
            else -> {
                // ignore
            }
        }

        runnable?.let {
            OSExecutors.getUnboundedPool().execute(it)
        }

        updateServiceShutdownTimeout(isPlayerStatusUpdate = false)

        return START_STICKY
    }

    private fun fireCurrentPlayerStatus() {
        val status = SBContextProvider.get().playerStatus
        playerStatusProcessor.onNext(status.toOptional())
    }

    private fun shouldAllowServiceTimeout(isPlayerStatusUpdate: Boolean): Boolean {
        OSAssert.assertMainThread()

        OSLog.d("Checking shouldAllowServiceTimeout(isPlayerStatusUpdate=$isPlayerStatusUpdate)")

        if (!isPlayerStatusUpdate && DeviceConnectivity.instance.deviceConnectivity) {
            OSLog.d("Eligible for shutdown with connectivity but no response from server (will be cancelled if server responds)")
            return true
        }

        val playMode = SBContextProvider.get().playerStatus?.mode

        // any of these conditions will cause the service to shut itself down
        return when (eligibleForShutdown) {
            EligibleForShutdown.NO -> {
                OSLog.d("NOT eligible for shutdown")
                false
            }
            EligibleForShutdown.IFSTOPPED -> {
                if (playMode == PlayerStatus.Mode.STOPPED) {
                    OSLog.d("Eligible for shutdown because current player is NOT paused or playing")
                    true
                } else {
                    OSLog.d("NOT eligible for shutdown because current player is paused or playing")
                    false
                }
            }
            EligibleForShutdown.IFPAUSED -> {
                if (playMode == PlayerStatus.Mode.STOPPED || playMode == PlayerStatus.Mode.PAUSED) {
                    OSLog.d("Eligible for shutdown because current player is NOT playing")
                    true
                } else {
                    OSLog.d("NOT eligible for shutdown because current player is playing")
                    false
                }

            }
        }
    }

    @Subscribe
    fun whenConnectionStateChanges(state: ConnectionState) {
        OSExecutors.getSingleThreadScheduledExecutor().execute {
            WidgetCommon.updateWidgets(SBContextProvider.get())
        }
    }

    @Subscribe
    fun whenCurrentPlayerStatusChanges(event: CurrentPlayerState) {
        playerStatusProcessor.onNext(event.playerStatus.toOptional())
    }

    private fun onDeviceStatusChange() {
        OSAssert.assertMainThread()

        val connectivity = DeviceConnectivity.instance.deviceConnectivity

        if (Build.VERSION.SDK_INT >= 23) {
            idleMode = powerManager?.isDeviceIdleMode == true
        }

        updateChargingStatus()

        val newEligibleForShutdown: EligibleForShutdown

        // we no longer worry about metered/unmetered since there's a notification. also doze.
        // val isMetered = ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager)
        if (!uiGone) {
            // ui is visible, no shutdown
            newEligibleForShutdown = EligibleForShutdown.NO
        } else {
            newEligibleForShutdown = EligibleForShutdown.IFSTOPPED
        }
        if (OSLog.isLoggable(OSLog.DEBUG)) {
            val toString = MoreObjects.toStringHelper("ServerConnectionService status change")
                    .add("isCharging", isCharging)
                    .add("idleMode", idleMode)
                    .add("hasConnectivity", connectivity)
                    .add("isShutdownEligible", newEligibleForShutdown).toString()
            OSLog.d(toString)
        }
        eligibleForShutdown = newEligibleForShutdown

        updateServiceShutdownTimeout(isPlayerStatusUpdate = false)
    }

    private fun updateChargingStatus() {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, ifilter)
        if (batteryStatus != null) {
            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
    }

    private fun updateServiceShutdownTimeout(isPlayerStatusUpdate: Boolean) {
        OSAssert.assertMainThread()

        if (shouldAllowServiceTimeout(isPlayerStatusUpdate = isPlayerStatusUpdate)) {
            setShutdownTimeoutInMillis(SERVICE_TIMEOUT_IN_MILLIS)
        } else {
            clearShutdownTimeout()
        }
    }

    private fun setShutdownTimeoutInMillis(timeout: Long) {
        if (pendingShutdownDisposable == null) {
            pendingShutdownDisposable = AndroidSchedulers.mainThread().scheduleDirect({
                OSLog.d("Shutting connections down on schedule")

                stopSelf()
            }, timeout, TimeUnit.MILLISECONDS)
        }
    }

    private fun clearShutdownTimeout() {
        pendingShutdownDisposable?.let {
            it.dispose()
            pendingShutdownDisposable = null
        }
    }

    private inner class RemoteBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            OSAssert.assertMainThread()

            val action = intent.action ?: return

            var unhandled = false
            @Suppress("DEPRECATION")
            when (action) {
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    // do nothing
                }
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                    // do nothing
                }
                BroadcastServiceActions.STOP_SERVICE.intentAction -> {
                    doStopSelf(0)
                }
                BroadcastServiceActions.UPDATE_WIDGETS.intentAction -> {
                    OSExecutors.getUnboundedPool().execute(ConnectionAwareRunnable(location = "UPDATE_WIDGETS") {
                        AndroidSchedulers.mainThread().scheduleDirect {
                            playerStatusProcessor.onNext(it.playerStatus.toOptional())
                        }
                    })
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    if (SBPreferences.get().shouldPauseOnHeadphoneDisconnect()) {
                        OSExecutors.getUnboundedPool().execute(ConnectionAwareRunnable(location = "ACTION_AUDIO_BECOMING_NOISY") {
                            if (it.playerStatus?.mode == PlayerStatus.Mode.PLAYING) {
                                PlayerCommands.sendPause()
                            }
                        })
                    }
                }
                else -> {
                    // unhandled action
                    unhandled = true
                }
            }

            if (!unhandled) {
                onDeviceStatusChange()
            }
        }

        /** defer stopping service until foreground promotion has occurred or eventually just try it */
        private fun doStopSelf(recurseCount: Int) {
            if (!serviceMayBeStarted) return

            if (!needsForegroundPromotion || recurseCount > 3) {
                OSLog.v("Service stopped")
                NotificationCommon.cancelNowPlayingNotification(this@ServerConnectionService)
                stopSelf()
                serviceWasStopped = true
            } else {
                AndroidSchedulers.mainThread().scheduleDirect({
                    doStopSelf(recurseCount + 1)
                }, 5, TimeUnit.SECONDS)
            }
        }
    }

    private fun wouldShowNotification(status: PlayerStatus?): Boolean {
        if (!SBPreferences.get().shouldShowNowPlayingNotification()) {
            return false
        }

        val mode = status?.mode ?: PlayerStatus.Mode.STOPPED
        if (mode == PlayerStatus.Mode.PLAYING) {
            return true
        }
        if (mode == PlayerStatus.Mode.PAUSED && !lastWasPausedWithUi) {
            return true
        }

        return false
    }

    inner class LocalBinder : Binder() {
        fun addUiRef() {
            if (uiGone) {
                NotificationCommon.cancelNowPlayingNotification(this@ServerConnectionService)
            }

            try {
                // start service with application context
                applicationContext.let {
                    val intent = getIntent(it, ServiceActions.SERVICE_TO_BACKGROUND)
                    it.startService(intent)
                }
            } catch (e: IllegalStateException) {
                // ignore, server already gone
            }

            uiRefCount++

            fireCurrentPlayerStatus()
        }

        fun removeUiRef() {
            uiRefCount--

            if (uiGone) {
                if (wouldShowNotification(SBContextProvider.get().playerStatus)) {
                    // promote to foreground service

                    try {
                        applicationContext.let {
                            val intent = getIntent(it, ServiceActions.SERVICE_TO_FOREGROUND)
                            ContextCompat.startForegroundService(it, intent)
                        }
                    } catch (e: IllegalStateException) {
                        // ignore, server already gone
                    }
                }
            }
            fireCurrentPlayerStatus()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}
