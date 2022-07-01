/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import okio.Sink;

/**
 * A counting source.
 */
public interface CountingSink extends Sink {

  long getWriteCount();

  void resetWriteCount();
}