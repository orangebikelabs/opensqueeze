/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.util.SparseIntArray
import android.view.View
import androidx.core.app.ActivityCompat
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.compat.Compat
import com.orangebikelabs.orangesqueeze.compat.CompatKt
import com.orangebikelabs.orangesqueeze.compat.stopForegroundCompat
import com.orangebikelabs.orangesqueeze.nowplaying.PlayerControlStates
import com.orangebikelabs.orangesqueeze.startup.StartupActivity
import com.orangebikelabs.orangesqueeze.ui.MainActivity
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * @author tsandee
 */
object NotificationCommon {

    const val CACHE_NOTIFICATION_CHANNEL_ID = "cache"
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "downloads"
    const val NOWPLAYING_NOTIFICATION_CHANNEL_ID = "nowplaying"

    const val NOWPLAYING_NOTIFICATION_ID = 1

    private var createdChannels = false

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        if (createdChannels) {
            return
        }
        createdChannels = true

        val notificationManager = NotificationManagerCompat.from(context)

        val channelList = mutableListOf<NotificationChannel>()

        channelList += NotificationChannel(DOWNLOAD_NOTIFICATION_CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        channelList += NotificationChannel(CACHE_NOTIFICATION_CHANNEL_ID, "Cache", NotificationManager.IMPORTANCE_LOW)
        channelList += NotificationChannel(NOWPLAYING_NOTIFICATION_CHANNEL_ID, "Now Playing", NotificationManager.IMPORTANCE_LOW)

        channelList.forEach {
            it.setShowBadge(false)
            notificationManager.createNotificationChannel(it)
        }
    }

    fun showEmptyNotification(service: Service) {
        if (ActivityCompat.checkSelfPermission(service, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val context = service.applicationContext

        val notificationManager = NotificationManagerCompat.from(context)
        createNotificationChannels(context)

        val builder = NotificationCompat.Builder(context, NOWPLAYING_NOTIFICATION_CHANNEL_ID)

        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(buildNowPlayingPendingIntent(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setShowWhen(false)
                .setContentText("empty")
                .setColorized(true)
                .setColor(ContextCompat.getColor(context, R.color.notification))
                .setLocalOnly(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notification = builder.build()

        notificationManager.notify(null, NOWPLAYING_NOTIFICATION_ID, notification)
        service.startForeground(NOWPLAYING_NOTIFICATION_ID, notification)
    }

    fun showNowPlayingNotification(service: Service, status: PlayerStatus, sessionToken: MediaSessionCompat.Token?) {
        if (ActivityCompat.checkSelfPermission(service, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val context = service.applicationContext

        createNotificationChannels(context)

        // set baseline for controls
        val visibilities = SparseIntArray()

        PlayerControlStates.putViewVisibilities(status, visibilities)

        val notificationManager = NotificationManagerCompat.from(context)

        val builder = NotificationCompat.Builder(context, NOWPLAYING_NOTIFICATION_CHANNEL_ID)

        builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(buildNowPlayingPendingIntent(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setShowWhen(false)
                .setContentTitle(status.track)
                .setContentText(status.displayArtist)
                .setColorized(true)
                .setColor(ContextCompat.getColor(context, R.color.notification))
                .setLocalOnly(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (status.album.isNotBlank()) {
            builder.setSubText(status.album)
        }

        val largeBitmap = getLargeBitmap(status)
        if (largeBitmap != null) {
            builder.setLargeIcon(largeBitmap)
        }
        var compactActionIndex = 0
        val compactActionIndexList = mutableListOf<Int>()
        if (visibilities.get(R.id.thumbsdown_button) == View.VISIBLE) {
            compactActionIndexList.add(compactActionIndex)
            compactActionIndex++
            builder.addAction(R.drawable.ic_thumb_down_white, "Thumbs Down",
                    buildThumbsDownButtonIntent(context, status.id))
        } else if (visibilities.get(R.id.previous_button) == View.VISIBLE) {
            compactActionIndex++
            builder.addAction(R.drawable.ic_skip_previous_white, "Previous",
                    buildCommandPendingIntent(context, R.id.previous_button, status.id,
                            PlayerCommands.PREVIOUS_TRACK))
        }
        if (visibilities.get(R.id.pause_button) == View.VISIBLE) {
            compactActionIndexList.add(compactActionIndex)
            compactActionIndex++
            builder.addAction(R.drawable.ic_pause_white, "Pause",
                    buildCommandPendingIntent(context, R.id.pause_button, status.id,
                            PlayerCommands.PLAYPAUSE_TOGGLE))
        }
        if (visibilities.get(R.id.play_button) == View.VISIBLE) {
            compactActionIndexList.add(compactActionIndex)
            compactActionIndex++
            builder.addAction(R.drawable.ic_play_arrow_white, "Play",
                    buildCommandPendingIntent(context, R.id.play_button, status.id,
                            PlayerCommands.PLAYPAUSE_TOGGLE))
        }
        if (visibilities.get(R.id.thumbsup_button) == View.VISIBLE) {
            compactActionIndexList.add(compactActionIndex)
            @Suppress("UNUSED_CHANGED_VALUE")
            compactActionIndex++
            builder.addAction(R.drawable.ic_thumb_up_white, "Thumbs Up",
                    buildThumbsUpButtonIntent(context, status.id))
        } else if (visibilities.get(R.id.next_button) == View.VISIBLE) {
            compactActionIndexList.add(compactActionIndex)
            @Suppress("UNUSED_CHANGED_VALUE")
            compactActionIndex++
            builder.addAction(R.drawable.ic_skip_next_white, "Next",
                    buildCommandPendingIntent(context, R.id.next_button, status.id,
                            PlayerCommands.NEXT_TRACK))
        }

        val stopServicePendingIntent = buildStopServicePendingIntent(context)

        // show cancel/close notification button
        compactActionIndexList.add(compactActionIndex)
        compactActionIndex++
        @Suppress("UNUSED_CHANGED_VALUE")
        compactActionIndex++
        builder.addAction(R.drawable.ic_close_white, "Close", stopServicePendingIntent)


        val styleBuilder = MediaStyle(builder)
                .setMediaSession(sessionToken)

        when (compactActionIndexList.size) {
            1 -> styleBuilder.setShowActionsInCompactView(compactActionIndexList[0])
            2 -> styleBuilder.setShowActionsInCompactView(compactActionIndexList[0], compactActionIndexList[1])
            3 -> styleBuilder.setShowActionsInCompactView(compactActionIndexList[0], compactActionIndexList[1], compactActionIndexList[2])
        }

        builder.setStyle(styleBuilder)

        val notification = builder.build()

        notificationManager.notify(null, NOWPLAYING_NOTIFICATION_ID, notification)
        service.startForeground(NOWPLAYING_NOTIFICATION_ID, notification)
    }

    fun cancelNowPlayingNotification(service: Service) {
        service.stopForegroundCompat(CompatKt.STOP_FOREGROUND_REMOVE)
    }

    private fun getLargeBitmap(status: PlayerStatus): Bitmap? {
        val context = SBContextProvider.get().applicationContext
        var retval: Bitmap? = null
        try {
            val artwork = status.artwork

            val largeBitmapSize = context.resources.getDimensionPixelSize(R.dimen.notification_bigpicture_bitmap_size)
            val future = artwork.getThumbnail(largeBitmapSize)
            if (!future.isDone) {
                Futures.addCallback(future, object : FutureCallback<Bitmap> {
                    override fun onSuccess(result: Bitmap?) {
                        if (result != null) {
                            val applicationContext = context.applicationContext
                            val intent = ServerConnectionService.getBroadcastIntent(ServerConnectionService.BroadcastServiceActions.UPDATE_WIDGETS)
                            applicationContext.sendBroadcast(intent)
                        }
                    }

                    override fun onFailure(t: Throwable) {
                        OSLog.e(t.message ?: "", t)
                    }
                }, OSExecutors.getMainThreadExecutor())
            }
            retval = future.get(0, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            // no worries
        } catch (e: InterruptedException) {
            // empty, ok
        } catch (e: ExecutionException) {
            OSLog.e(e.message ?: "", e)
        }

        return retval
    }

    fun buildStopServicePendingIntent(context: Context): PendingIntent {
        val intent = ServerConnectionService.getBroadcastIntent(ServerConnectionService.BroadcastServiceActions.STOP_SERVICE)
        return PendingIntent.getBroadcast(context, 0, intent, Compat.getDefaultPendingIntentFlags() or PendingIntent.FLAG_ONE_SHOT)
    }

    private fun buildNowPlayingPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, StartupActivity::class.java)
        intent.data = Uri.parse("https://app.orangebikelabs.com/orangesqueeze/nowplaying")

        return PendingIntent.getActivity(context, 0, intent, Compat.getDefaultPendingIntentFlags() or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildSearchPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        intent.action = Intent.ACTION_SEARCH

        return PendingIntent.getActivity(context, 0, intent, Compat.getDefaultPendingIntentFlags() or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildThumbsUpButtonIntent(context: Context, playerId: PlayerId): PendingIntent {
        val intent = ServerConnectionService.getIntent(context, ServerConnectionService.ServiceActions.THUMBSUP)
        return PendingIntent.getService(context, 0, intent, Compat.getDefaultPendingIntentFlags() or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildThumbsDownButtonIntent(context: Context, playerId: PlayerId): PendingIntent {
        val intent = ServerConnectionService.getIntent(context, ServerConnectionService.ServiceActions.THUMBSDOWN)
        return PendingIntent.getService(context, 0, intent, Compat.getDefaultPendingIntentFlags() or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildCommandPendingIntent(context: Context, id: Int, playerId: PlayerId, commands: List<String>): PendingIntent {
        val intent = ServerConnectionService.getIntent(context, ServerConnectionService.ServiceActions.SEND_COMMANDS)
        intent.putExtra(ServerConnectionService.EXTRA_COMMANDS, commands.toTypedArray())
        intent.putExtra(ServerConnectionService.EXTRA_PLAYER, playerId.toString())

        return PendingIntent.getService(context, id, intent, Compat.getDefaultPendingIntentFlags() or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
