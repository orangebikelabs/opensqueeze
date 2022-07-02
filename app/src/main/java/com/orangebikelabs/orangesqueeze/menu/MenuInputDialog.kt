/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.menu

import android.text.InputType
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.datetime.timePicker
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.SBPreferences
import java.util.*
import javax.annotation.Nonnull

/**
 * General input dialog.<br></br>
 *
 * @author tbsandee@orangebikelabs.com
 */
object MenuInputDialog {
    @JvmStatic
    @Nonnull
    fun newInstance(fragment: Fragment, element: MenuElement, action: MenuAction?): MaterialDialog {
        return MaterialDialog(fragment.requireContext()).apply {
            var lastTime: Calendar = Calendar.getInstance()

            lifecycleOwner(fragment)

            cancelable(true)
            noAutoDismiss()
            cancelOnTouchOutside(false)

            val inputElement = element.input
            val menuTitle = if (element.id == "settingsPlayerNameChange") {
                fragment.getString(R.string.rename_player_title)
            } else {
                inputElement?.title ?: ""
            }
            title(text = menuTitle)

            val inputStyle = inputElement?.inputStyle
            if (inputStyle == "time") {
                val value = inputElement.initialText.toIntOrNull() ?: 0
                val hour = value / 3600
                val remainingSeconds = value - hour * 3600
                val minute = remainingSeconds / 60

                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                timePicker(currentTime = calendar, show24HoursView = SBPreferences.get().shouldUse24HourTimeFormat()) { _, cal ->
                    lastTime = cal
                }
            } else {
                // Set an EditText view to get user input
                val initialText = inputElement?.initialText
                input(inputType = InputType.TYPE_CLASS_TEXT, hint = menuTitle, prefill = initialText) { _, _ -> }
            }
            positiveButton(res = R.string.ok) {
                val replacementValue = if (inputStyle == "time") {
                    val seconds = lastTime.get(Calendar.HOUR_OF_DAY) * 3600 + lastTime.get(Calendar.MINUTE) * 60
                    seconds.toString()
                } else {
                    getInputField().text.toString()
                }
                val target = fragment as AbsMenuFragment
                if (action != null) {
                    val params = MenuHelpers.buildSearchParametersAsList(element, action, replacementValue, false)
                    if (params != null) {
                        val nextWindow = MenuHelpers.getNextWindow(element, action)
                        target.executeMenuCommand(replacementValue, element, action, params, nextWindow, true, null)
                    }
                }
                it.dismiss()
            }
            negativeButton(res = R.string.cancel) {
                it.dismiss()
            }
        }
    }
}