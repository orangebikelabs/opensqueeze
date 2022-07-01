/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import java.io.IOException;

import javax.annotation.Nonnull;

import okio.Source;

/**
 * Supplier of buffered source objects.
 */
public interface SourceSupplier {
  @Nonnull
  Source getSource() throws IOException;
}