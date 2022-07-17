/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import com.google.common.util.concurrent.Futures
import com.orangebikelabs.orangesqueeze.common.*
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Function
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Broadcast receiver that launches an intent directed towards the server connection service, which mutes any recently connected players.
 *
 * @author tbsandee@orangebikelabs.com
 */
class PhoneStateReceiver : BroadcastReceiver() {
    private var dummy: Disposable? = null
    private val prefs by lazy { SBPreferences.get() }

    @SuppressLint("CheckResult")
    override fun onReceive(context: Context, intent: Intent) {
        OSLog.d("onReceive intent=$intent")
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        // should we do this in a background thread? maybe
        var work: Function<SBContext, List<FutureResult?>>? = null
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            // only trigger this if something will actually happen otherwise we spin up the server connection for nothing
            if (prefs.getAutoUnmute(TimeUnit.SECONDS) != null) {
                work = Function { sbContext: SBContext -> doUnmute(prefs, sbContext) }
            }
        } else {
            // only trigger this if something will actually happen otherwise we spin up the server connection for nothing
            if (prefs.onCallBehavior != OnCallMuteBehavior.NOTHING) {
                work = Function { sbContext: SBContext -> doMute(prefs, sbContext) }
            }
        }
        if (work != null) {
            val pendingResult = goAsync()
            val sbContext = SBContextProvider.initializeAndGet(context)
            sbContext.temporaryOnStart(context, 10, TimeUnit.SECONDS)
            dummy = Single.just(sbContext)
                    .subscribeOn(OSExecutors.singleThreadScheduler())
                    .map(work)
                    .doOnSuccess { results ->
                        val allFuture = Futures.successfulAsList(results)
                        try {
                            allFuture.get(8, TimeUnit.SECONDS)
                        } catch (e: TimeoutException) {
                            // ignore
                        } catch (e: InterruptedException) {
                            // ignore
                        } catch (e: ExecutionException) {
                            OSLog.w(e.cause?.message, e.cause)
                        }
                    }
                    .timeout(8, TimeUnit.SECONDS)
                    .doFinally {
                        pendingResult.finish()
                    }
                    .subscribe()
        }
    }

    companion object {
        fun updateComponentEnabled(context: Context, prefs: SBPreferences) {
            val enabled = prefs.onCallBehavior != OnCallMuteBehavior.NOTHING
            val mgr = context.packageManager
            mgr.setComponentEnabledSetting(ComponentName(context, PhoneStateReceiver::class.java), if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }

        /**
         * perform the mute action when a phone call is received, if configured
         */
        private fun doMute(prefs: SBPreferences, context: SBContext): List<FutureResult> {
            // shortcircuit if disabled
            if (prefs.onCallBehavior == OnCallMuteBehavior.NOTHING) {
                return emptyList()
            }

            val results = mutableListOf<FutureResult>()

            val currentPlayerId = context.playerId
            val mutedPlayers = mutableListOf<PlayerId>()
            context.serverStatus.availablePlayers
                    .filter { it.mode == PlayerStatus.Mode.PLAYING }
                    .forEach { itPlayerStatus ->
                        val request = when (prefs.onCallBehavior) {
                            OnCallMuteBehavior.MUTE -> {
                                context.newRequest("mixer", "muting", "1")
                            }
                            OnCallMuteBehavior.MUTE_CURRENT -> {
                                if (itPlayerStatus.id == currentPlayerId) {
                                    context.newRequest("mixer", "muting", "1")
                                } else {
                                    null
                                }
                            }
                            OnCallMuteBehavior.PAUSE -> {
                                context.newRequest("pause", "1")
                            }
                            OnCallMuteBehavior.PAUSE_CURRENT -> {
                                if (itPlayerStatus.id == currentPlayerId) {
                                    context.newRequest("pause", "1")
                                } else {
                                    null
                                }
                            }
                            OnCallMuteBehavior.NOTHING -> {
                                // do nothing
                                null
                            }
                        }
                        if (request != null) {
                            mutedPlayers += itPlayerStatus.id

                            // add player id to request
                            request.playerId = itPlayerStatus.id

                            // submit the request and add it to the tracking list
                            results += request.submit(OSExecutors.getUnboundedPool())
                        }
                    }
            prefs.setLastMutedEvent(System.currentTimeMillis(), mutedPlayers)
            return results
        }

        private fun doUnmute(prefs: SBPreferences, context: SBContext): List<FutureResult?> {
            val unmutePref = prefs.getAutoUnmute(TimeUnit.MILLISECONDS) ?: return emptyList()

            // is there a valid mute event to process?
            val lastMuted = prefs.lastMutedTime
            if (lastMuted == 0L) {
                // nope, it either never happened or was processed already
                return emptyList()
            }

            // is it within the validity period?
            val threshold = unmutePref + lastMuted
            if (System.currentTimeMillis() > threshold) {
                // nope
                return emptyList()
            }
            val results = mutableListOf<FutureResult>()

            // capture muted player list
            val mutedPlayers = prefs.lastMutedPlayers

            // now clear the mute event
            prefs.setLastMutedEvent(0L, emptyList())
            for (playerId in mutedPlayers) {
                val request = when (prefs.onCallBehavior) {
                    OnCallMuteBehavior.MUTE, OnCallMuteBehavior.MUTE_CURRENT -> {
                        context.newRequest("mixer", "muting", "0")
                    }
                    OnCallMuteBehavior.PAUSE, OnCallMuteBehavior.PAUSE_CURRENT -> {
                        context.newRequest("pause", "0")
                    }
                    OnCallMuteBehavior.NOTHING -> {
                        null
                    }
                }
                if (request != null) {
                    request.playerId = playerId
                    results += request.submit(OSExecutors.getUnboundedPool())
                }
            }
            return results
        }
    }
}