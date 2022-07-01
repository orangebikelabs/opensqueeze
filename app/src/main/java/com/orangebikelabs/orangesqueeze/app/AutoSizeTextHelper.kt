/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app

import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import com.orangebikelabs.orangesqueeze.common.SBPreferences


class AutoSizeTextHelper {
    private val autoSizeEnabled by lazy { SBPreferences.get().shouldAutoSizeText() }

    fun applyAutoSize(tv: TextView?, maxLines: Int = 1) {
        if (tv == null || autoSizeEnabled) return

        TextViewCompat.setAutoSizeTextTypeWithDefaults(tv, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
        tv.maxLines = maxLines
        if (maxLines > 1) {
            tv.updateLayoutParams {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            tv.ellipsize = null
        }
    }
}