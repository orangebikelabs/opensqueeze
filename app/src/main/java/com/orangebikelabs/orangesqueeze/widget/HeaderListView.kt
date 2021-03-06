/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView

class HeaderListView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : ListView(context, attrs, defStyleAttr), HeaderCapable {
    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
}