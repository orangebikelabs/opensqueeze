/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnSliderTouchListener
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.SBDialogFragment
import com.orangebikelabs.orangesqueeze.common.FutureResult
import com.orangebikelabs.orangesqueeze.common.OSAssert
import com.orangebikelabs.orangesqueeze.common.PlayerId
import com.orangebikelabs.orangesqueeze.common.PlayerStatus
import com.orangebikelabs.orangesqueeze.common.event.CurrentPlayerState
import com.orangebikelabs.orangesqueeze.compat.getParcelableCompat
import com.orangebikelabs.orangesqueeze.databinding.VolumeDialogBinding
import com.squareup.otto.Subscribe
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A simple volume change popup dialog.<br>
 * It can be launched in a mode where it will automatically be dismissed upon inactivity.
 *
 * @author tbsandee@orangebikelabs.com
 */
class VolumeFragment : SBDialogFragment() {

    companion object {
        const val TAG = "VolumeFragment"

        private const val SMALL_INCREMENT_MIN = 1
        private const val SMALL_INCREMENT_MAX = 5
        private const val SMALL_INCREMENT_CURVE_DELAY = 750L
        private const val TIMEOUT_DELAY = 3000
        private const val ARG_PLAYERID = "playerId"
        private const val ARG_AUTOTIMEOUT = "autoTimeout"
        private const val MAX_VOLUME = 100f
        private const val MIN_VOLUME = 0f
        private val sHandler = Handler(Looper.getMainLooper())

        @JvmStatic
        fun newInstance(playerId: PlayerId, autoTimeout: Boolean): VolumeFragment {
            val fragment = VolumeFragment()
            val args = Bundle()
            args.putParcelable(ARG_PLAYERID, playerId)
            args.putBoolean(ARG_AUTOTIMEOUT, autoTimeout)
            OSAssert.assertParcelable(args)
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun isControlKeyCode(keyCode: Int): Boolean {
            return when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> true
                else -> false
            }
        }
    }

    private lateinit var binding: VolumeDialogBinding
    private var autoTimeout = false

    private var inTouch = false
    private var lastResult: FutureResult? = null
    private var autoTimeoutTime = 0L
    private var smallIncrementCurveFactor = 0
    private var lastSmallIncrementTime = 0L
    private var effectiveVolumeDragTimestamp = 0L
    private var playerStatus: PlayerStatus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // if we're reloading then just dismiss because user won't be expecting this transient dialog to be open still
            dismiss()
        }
        mBus.register(eventReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        mBus.unregister(eventReceiver)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val playerId = requireNotNull(requireArguments().getParcelableCompat(ARG_PLAYERID, PlayerId::class.java))
        autoTimeout = requireArguments().getBoolean(ARG_AUTOTIMEOUT)

        return MaterialDialog(requireContext()).apply {
            lifecycleOwner(this@VolumeFragment)
            cancelOnTouchOutside(true)
            customView(viewRes = R.layout.volume_dialog)
            binding = VolumeDialogBinding.bind(getCustomView())
            setOnKeyListener { _, keyCode, _ ->
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        controlChangeVolumeSmallUp()
                        true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        controlChangeVolumeSmallDown()
                        true
                    }
                    else -> {
                        false
                    }
                }
            }

            getCustomView().setOnTouchListener { v, event ->
                val maskedAction = event.actionMasked
                if (maskedAction == MotionEvent.ACTION_DOWN) {
                    effectiveVolumeDragTimestamp = SystemClock.uptimeMillis() + 250
                    return@setOnTouchListener true
                }
                if (maskedAction == MotionEvent.ACTION_MOVE) {
                    if (SystemClock.uptimeMillis() < effectiveVolumeDragTimestamp) {
                        return@setOnTouchListener true
                    }
                    if (v.width == 0) return@setOnTouchListener false

                    val width = v.width.toFloat()
                    val x = event.x
                    val clampedVolume = (x.coerceIn(MIN_VOLUME, width) / width * MAX_VOLUME).roundToInt()

                    lastResult = mContext.setPlayerVolume(playerId, clampedVolume)

                    updateVolumeText(clampedVolume)
                    setTimeout(true)

                    binding.volume.value = clampedVolume.toFloat()
                    return@setOnTouchListener true
                }
                return@setOnTouchListener true
            }
            binding.volume.addOnChangeListener { _, value, fromUser ->
                val status = playerStatus
                if (fromUser && status != null) {
                    lastResult = mContext.setPlayerVolume(status.id, value.toInt())
                    updateVolumeText(value.toInt())
                    setTimeout(true)
                }
            }
            binding.volume.addOnSliderTouchListener(object : OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    inTouch = true
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    inTouch = false
                }
            })
            binding.volume.setLabelFormatter { value -> value.toInt().toString() }

            binding.volume.stepSize = 1f
            binding.volume.isTickVisible = false
            binding.volume.valueFrom = MIN_VOLUME
            binding.volume.valueTo = MAX_VOLUME
            binding.increaseVolumeButton.setOnClickListener {
                controlChangeVolumeSmallUp()
            }
            binding.decreaseVolumeButton.setOnClickListener {
                controlChangeVolumeSmallDown()
            }

            playerStatus = mContext.serverStatus.getPlayerStatus(playerId)
            updateView()
        }
    }

    override fun onStart() {
        super.onStart()
        setTimeout(true)
    }

    private val eventReceiver: Any = object : Any() {
        @Subscribe
        fun whenCurrentPlayerStatusChanges(event: CurrentPlayerState) {
            val status = event.playerStatus
                    ?: // no player active
                    return
            if (!isAdded) { // fragment not added
                return
            }
            if (playerStatus != null && status.id == playerStatus?.id) {
                playerStatus = status
                updateView()
            }
        }
    }

    fun controlChangeVolumeSmallUp() {
        if (playerStatus == null) {
            return
        }
        var diff = SMALL_INCREMENT_MIN
        if (smallIncrementCurveFactor > 0) {
            val current = SystemClock.uptimeMillis()
            if (current <= lastSmallIncrementTime + SMALL_INCREMENT_CURVE_DELAY) {
                diff = min(smallIncrementCurveFactor + 1, SMALL_INCREMENT_MAX)
            }
            lastSmallIncrementTime = current
        }
        smallIncrementCurveFactor = diff
        controlChangeVolume(diff)
    }

    fun controlChangeVolumeSmallDown() {
        if (playerStatus == null) {
            return
        }
        var diff = -SMALL_INCREMENT_MIN
        if (smallIncrementCurveFactor < 0) {
            val current = SystemClock.uptimeMillis()
            if (current <= lastSmallIncrementTime + SMALL_INCREMENT_CURVE_DELAY) {
                diff = max(smallIncrementCurveFactor - 1, -SMALL_INCREMENT_MAX)
                lastSmallIncrementTime = current
            }
            lastSmallIncrementTime = current
        }
        smallIncrementCurveFactor = diff
        controlChangeVolume(diff)
    }

    private fun controlChangeVolume(diff: Int) {
        val playerStatus = playerStatus ?: return
        val clampedVolume = (binding.volume.value + diff).coerceIn(MIN_VOLUME, MAX_VOLUME)
        binding.volume.value = clampedVolume
        lastResult = mContext.incrementPlayerVolume(playerStatus.id, diff)
        updateVolumeText(binding.volume.value.toInt())
        setTimeout(true)
    }

    private fun updateView() {
        val playerStatus = playerStatus ?: return
        if (!isAdded || inTouch) {
            return
        }
        lastResult.let {
            if (it == null || it.isCommitted) {
                var volume = playerStatus.volume.toFloat()
                if (playerStatus.isMuted) {
                    // TODO show graphic for muted?
                    volume = MIN_VOLUME
                }
                binding.volume.value = volume.coerceIn(MIN_VOLUME, MAX_VOLUME)
                updateVolumeText(volume.toInt())
                lastResult = null
            }
        }
    }

    private fun updateVolumeText(@Suppress("UNUSED_PARAMETER") volume: Int) {
        val playerStatus = playerStatus ?: return
        if (!isAdded) {
            return
        }
        binding.playerNameLabel.text = getString(R.string.player_label, playerStatus.name)
        binding.volumeLockedLabel.isVisible = playerStatus.isVolumeLocked
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // don't dismiss twice or we get an NPE
        sHandler.removeCallbacks(dismissRunnable)
    }

    private fun setTimeout(reset: Boolean) {
        if (!autoTimeout) return

        if (reset || autoTimeoutTime == 0L) {
            autoTimeoutTime = SystemClock.uptimeMillis() + TIMEOUT_DELAY
        }
        sHandler.removeCallbacks(dismissRunnable)
        sHandler.postAtTime(dismissRunnable, autoTimeoutTime)
    }

    private val dismissRunnable = Runnable {
        try {
            dismissAllowingStateLoss()
        } catch (e: Throwable) { // ignore
        }
    }
}