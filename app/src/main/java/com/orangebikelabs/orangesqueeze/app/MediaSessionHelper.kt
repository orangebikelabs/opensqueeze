/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.support.v4.media.MediaMetadataCompat
import androidx.media.VolumeProviderCompat
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.common.event.AppPreferenceChangeEvent
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState
import com.squareup.otto.Subscribe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Media control compat helper.
 */
class MediaSessionHelper private constructor(private val context: Context) {

    companion object {
        fun newInstance(context: Context): MediaSessionHelper {
            OSAssert.assertApplicationContext(context)

            return MediaSessionHelper(context)
        }

        private fun scaleBitmapIfTooBig(inBitmap: Bitmap, maxWidth: Int, maxHeight: Int, targetConfig: Bitmap.Config): Bitmap {
            val retval: Bitmap

            val width = inBitmap.width
            val height = inBitmap.height
            if (width > maxWidth || height > maxHeight) {
                val scale = min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
                val newWidth = (scale * width).roundToInt()
                val newHeight = (scale * height).roundToInt()
                val outBitmap = Bitmap.createBitmap(newWidth, newHeight, targetConfig)
                val canvas = Canvas(outBitmap)
                val paint = Paint()
                paint.isAntiAlias = true
                paint.isFilterBitmap = true
                canvas.drawBitmap(inBitmap, null, RectF(0f, 0f, outBitmap.width.toFloat(), outBitmap.height.toFloat()), paint)
                retval = outBitmap
            } else {
                retval = inBitmap
            }
            return retval
        }

        val tokenSubject: BehaviorSubject<MediaSessionCompat.Token> = BehaviorSubject.create()

        private const val NATIVE_SQUEEZEBOX_MAX_VOLUME = 100
    }

    private var session: MediaSessionCompat? = null

    private val displayWidth by lazy { context.resources.displayMetrics.widthPixels }
    private val displayHeight by lazy { context.resources.displayMetrics.heightPixels }

    private val mediaKeySilentAudioHack by lazy { MediaKeySilentAudioHack(context) }

    private val metadataBuilder = MediaMetadataCompat.Builder()

    val token: MediaSessionCompat.Token?
        get() = session?.sessionToken

    @Subscribe
    fun whenActivePlayerStatusChanges(event: CurrentPlayerState) {
        updatePlayerStatus(event.playerStatus, null)
    }

    @Subscribe
    fun whenPreferenceChanges(event: AppPreferenceChangeEvent) {
        if (event.key == context.getString(R.string.pref_systemvolumecontrol_key)) {
            updateVolumeStatus()
        }
    }

    fun handleMediaButtonIntent(intent: Intent) {
        MediaButtonReceiver.handleIntent(session, intent)
    }

    fun register() {
        BusProvider.getInstance().register(this)

        session = MediaSessionCompat(context, "tag").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onSeekTo(newPositionMs: Long) {
                    var newPosition = newPositionMs.toDouble()
                    newPosition /= 1000.0

                    SBContextProvider.get().sendPlayerCommand("time", newPosition.toString())
                }

                override fun onPause() {
                    PlayerCommands.sendPause()
                }

                override fun onPlay() {
                    PlayerCommands.sendPlay()
                }

                override fun onSkipToNext() {
                    PlayerCommands.sendNextTrack()
                }

                override fun onSkipToPrevious() {
                    PlayerCommands.sendPreviousTrack()
                }

                override fun onStop() {
                    PlayerCommands.sendStop()
                }
            })
            setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f).build())
            isActive = true
            tokenSubject.onNext(sessionToken)
        }
        updateVolumeStatus()


        // force status update next in queue
        AndroidSchedulers.mainThread().scheduleDirect {
            updatePlayerStatus(SBContextProvider.get().playerStatus)
        }
    }

    fun unregister() {
        session?.release()
        session = null

        mediaKeySilentAudioHack.close()

        BusProvider.getInstance().unregister(this)
    }

    private fun updateVolumeStatus() {
        if (SBPreferences.get().shouldUseVolumeIntegration()) {
            session?.setPlaybackToRemote(volumeProvider)
        } else {
            session?.setPlaybackToLocal(AudioManager.STREAM_SYSTEM)
        }
    }

    private fun updatePlayerStatus(status: PlayerStatus?, loadedLargeBitmap: Bitmap? = null) {
        if (session == null) {
            mediaKeySilentAudioHack.state = MediaKeySilentAudioHack.State.NONE
            return
        }

        if (status == null) {
            session?.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f).build())
            mediaKeySilentAudioHack.state = MediaKeySilentAudioHack.State.NONE
            return
        }


        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, status.album)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, status.displayArtist)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, status.trackArtist)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, status.track)

        val duration = (status.totalTime * 1000).toLong()
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        val trackNumber = status.trackNumber.orNull()?.toLongOrNull()
        if (trackNumber != null) {
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber)
        }

        val year = status.year.orNull()?.toLongOrNull()
        if (year != null) {
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_YEAR, year)
        }

        var largeBitmap = loadedLargeBitmap
        if (largeBitmap == null) {
            largeBitmap = getLargeBitmap(status)
        }
        largeBitmap?.let {
            putArtwork(it)
        }

        // set volume in squeezebox units, different from volume provider
        volumeProvider.scaledSqueezeboxVolume = status.volume
        session?.setMetadata(metadataBuilder.build())

        val psBuilder = PlaybackStateCompat.Builder()
        val playbackState: Int
        var position: Long = 0
        var speed = 0.0f

        when (status.mode) {
            PlayerStatus.Mode.STOPPED -> {
                playbackState = PlaybackStateCompat.STATE_STOPPED
                mediaKeySilentAudioHack.state = MediaKeySilentAudioHack.State.STOPPED
            }
            PlayerStatus.Mode.PLAYING -> {
                speed = 1.0f
                position = (status.getElapsedTime(true) * 1000).toLong()
                playbackState = PlaybackStateCompat.STATE_PLAYING
                mediaKeySilentAudioHack.state = MediaKeySilentAudioHack.State.PLAYING
            }
            PlayerStatus.Mode.PAUSED -> {
                position = (status.getElapsedTime(true) * 1000).toLong()
                playbackState = PlaybackStateCompat.STATE_PAUSED
                mediaKeySilentAudioHack.state = MediaKeySilentAudioHack.State.PAUSED
            }
        }
        psBuilder.setActions(PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SET_RATING
        )
        psBuilder.setState(playbackState, position, speed)
        session?.setPlaybackState(psBuilder.build())
    }

    private fun putArtwork(artwork: Bitmap?) {
        var actualArtwork: Bitmap? = null
        if (artwork != null) {
            val config = Bitmap.Config.RGB_565
            actualArtwork = scaleBitmapIfTooBig(artwork, displayWidth, displayHeight, config)
        }
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, actualArtwork)
    }

    private val volumeProvider = object : VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, 50, 0) {
        private val scaleRatio = NATIVE_SQUEEZEBOX_MAX_VOLUME / maxVolume

        var scaledSqueezeboxVolume: Int
            get() = currentVolume * scaleRatio
            set(value) {
                currentVolume = (value.toDouble() / scaleRatio).roundToInt()
            }

        override fun onSetVolumeTo(volume: Int) {
            currentVolume = volume

            SBContextProvider.get().apply {
                playerId?.let {
                    setPlayerVolume(it, scaledSqueezeboxVolume)
                }
            }
        }

        override fun onAdjustVolume(direction: Int) {
            val pid = SBContextProvider.get().playerId ?: return
            val helper = VolumeCommandHelper.getInstance(pid)
            val increment = direction * scaleRatio
            if (increment != 0) {
                helper.incrementPlayerVolume(increment)
                currentVolume += direction
            }
        }
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
                        updatePlayerStatus(status, result)
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
        } catch (e: ExecutionException) {
            OSLog.e(e.message ?: "", e)
        }

        return retval
    }

}
