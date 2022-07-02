/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import okio.Source;

/**
 * A counting source.
 */
public interface CountingSource extends Source {

  long getReadCount();

  void resetReadCount();
}