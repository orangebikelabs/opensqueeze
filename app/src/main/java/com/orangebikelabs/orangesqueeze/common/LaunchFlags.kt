/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common

object LaunchFlags {
    fun reset() {
        gotoNowPlaying = false
    }

    var gotoNowPlaying: Boolean = false
}