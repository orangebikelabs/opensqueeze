/*
 * Copyright (c) 2023 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common

import android.content.Context
import android.content.pm.PackageManager

/**
 * Returns true if the supplied app package is installed. On Android 11+, you must request permission
 * to query the package manager for requested package.
 */
fun Context.isAppInstalled(packageName: String): Boolean {
    return try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (ignore: PackageManager.NameNotFoundException) {
        false
    }
}