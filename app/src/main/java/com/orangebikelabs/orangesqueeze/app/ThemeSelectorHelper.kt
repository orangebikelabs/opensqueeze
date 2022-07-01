/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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