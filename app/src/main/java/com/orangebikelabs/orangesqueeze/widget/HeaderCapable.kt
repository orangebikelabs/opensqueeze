/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */
package com.orangebikelabs.orangesqueeze.widget

import android.view.View

interface HeaderCapable {
    fun addHeaderView(v: View, data: Any?, isSelectable: Boolean)
}