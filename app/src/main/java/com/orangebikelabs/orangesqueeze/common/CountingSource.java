/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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