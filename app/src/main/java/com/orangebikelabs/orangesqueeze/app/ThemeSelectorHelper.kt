/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app

import com.orangebikelabs.orangesqueeze.common.SBPreferences
import com.orangebikelabs.orangesqueeze.common.ThemeSelector

class ThemeSelectorHelper {

    fun getThemeDefinition(): ThemeSelector {
        return if (compactMode) {
            ThemeSelector.COMPACT
        } else {
            ThemeSelector.NORMAL
        }
    }

    var compactMode: Boolean = SBPreferences.get().isCompactMode
}