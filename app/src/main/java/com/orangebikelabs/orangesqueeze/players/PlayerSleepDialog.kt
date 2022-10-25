/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.players

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.google.android.material.slider.Slider
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.databinding.ManageplayersSleepBinding
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @author tbsandee@orangebikelabs.com
 */
class PlayerSleepDialog private constructor(private val lifecycleOwner: LifecycleOwner, private val context: Context, private val playerStatus: PlayerStatus, private val callback: OnSetPlayerSleep) {

    interface OnSetPlayerSleep {
        fun setPlayerSleep(playerId: PlayerId, sleepTime: Long, sleepUnit: TimeUnit)
    }

    companion object {
        private const val SEEKER_MULTIPLIER = 10 // minutes
        private const val MAX_SLEEP_TIME = 180 // minutes

        private const val MIN_SLEEP_SEEK_BAR = 0f
        private const val MAX_SLEEP_SEEK_BAR = MAX_SLEEP_TIME.toFloat() / SEEKER_MULTIPLIER

        @JvmStatic
        fun create(fragment: Fragment, playerId: PlayerId, callback: OnSetPlayerSleep): MaterialDialog {
            val playerStatus = SBContextProvider.get().serverStatus.getCheckedPlayerStatus(playerId)
            val container = PlayerSleepDialog(fragment, fragment.requireContext(), playerStatus, callback)
            return container.create()
        }
    }

    private lateinit var binding: ManageplayersSleepBinding

    private val minutes: Int
        get() = binding.sleepBar.value.toInt() * SEEKER_MULTIPLIER

    private fun create(): MaterialDialog {
        return MaterialDialog(context).apply {
            lifecycleOwner(lifecycleOwner)

            cancelable(true)
            customView(R.layout.manageplayers_sleep, scrollable = true)
            binding = ManageplayersSleepBinding.bind(getCustomView())

            initViews()
            positiveButton(res = R.string.ok) {
                callback.setPlayerSleep(playerStatus.id, minutes * 60.toLong(), TimeUnit.SECONDS)
            }
            negativeButton(res = R.string.cancel)
        }
    }

    private fun initViews() {

        binding.timePicker.setIs24HourView(SBPreferences.get().shouldUse24HourTimeFormat())
        binding.sleepBar.valueFrom = MIN_SLEEP_SEEK_BAR
        binding.sleepBar.valueTo = MAX_SLEEP_SEEK_BAR
        binding.sleepBar.value = (SBPreferences.get().getLastPlayerSleepTime(TimeUnit.SECONDS).toInt() / 60f / SEEKER_MULTIPLIER)
                .coerceIn(MIN_SLEEP_SEEK_BAR, MAX_SLEEP_SEEK_BAR)
        binding.sleepBar.addOnChangeListener(Slider.OnChangeListener { _, _, fromUser ->
            if (fromUser) {
                updateText()
                updateTimePicker()
            }
        })
        binding.timePicker.setOnTimeChangedListener { _, hourOfDay, minute ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.HOUR_OF_DAY, hourOfDay)

            val current = Calendar.getInstance()
            if (cal.before(current)) {
                cal.add(Calendar.DATE, 1)
            }
            val diff = cal.timeInMillis - current.timeInMillis
            binding.sleepBar.value = (TimeUnit.MILLISECONDS.toMinutes(diff).toFloat() / SEEKER_MULTIPLIER)
                    .coerceIn(MIN_SLEEP_SEEK_BAR, MAX_SLEEP_SEEK_BAR)
            updateText()
        }

        updateText()
        updateTimePicker()
    }

    @Suppress("DEPRECATION")
    private fun updateTimePicker() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, binding.sleepBar.value.toInt() * SEEKER_MULTIPLIER)
        binding.timePicker.currentHour = cal.get(Calendar.HOUR_OF_DAY)
        binding.timePicker.currentMinute = cal.get(Calendar.MINUTE)
    }

    private fun updateText() {
        // for visual consistency, always use sleep time displayed by slider
        val sleepTime = binding.sleepBar.value.toInt() * SEEKER_MULTIPLIER
        if (sleepTime == 0) {
            val diff = playerStatus.totalTime - playerStatus.getElapsedTime(true)
            if (diff <= 0) {
                binding.playerSleepText.text = context.getString(R.string.player_sleep_never, playerStatus.name)
            } else {
                val timeLeft = diff.toInt() / 60
                binding.playerSleepText.text = context.getString(R.string.player_sleep_endoftrack, playerStatus.name, timeLeft)
            }
        } else {
            binding.playerSleepText.text = context.getString(R.string.player_sleep_text, playerStatus.name, sleepTime)
        }
    }
}