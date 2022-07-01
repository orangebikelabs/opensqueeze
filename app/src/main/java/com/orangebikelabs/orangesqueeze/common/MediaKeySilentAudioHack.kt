/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import com.orangebikelabs.orangesqueeze.R
import java.io.Closeable

/** This hack starts playing silent audio based on the current playing state.
 * Only necessary on Android 8.0 and higher.*/
class MediaKeySilentAudioHack(private val context: Context) : Closeable {
    enum class State { NONE, PLAYING, PAUSED, STOPPED }

    private var mediaPlayer: MediaPlayer? = null

    var state: State = State.NONE
        set(value) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            if (!SBPreferences.get().shouldEmitSilentAudio()) return

            if (field == value) return

            field = value
            when (value) {
                State.PLAYING -> {
                    if (mediaPlayer == null) {
                        mediaPlayer = MediaPlayer.create(context, R.raw.silence_1).apply {
                            isLooping = true
                            start()
                        }
                    }
                }
                else -> {
                    close()
                }
            }
        }

    override fun close() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}