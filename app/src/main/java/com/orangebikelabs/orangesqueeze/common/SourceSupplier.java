/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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