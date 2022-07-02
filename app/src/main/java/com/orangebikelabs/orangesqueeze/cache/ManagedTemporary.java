/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;

import java.io.Closeable;

import javax.annotation.Nonnull;

/**
 * Managed Temporary objects are used to transfer objects that may be larger than available memory. This is a temporary storage location
 * that is automatically freed when it is cleaned up.
 * <p/>
 *
 * @author tsandee
 */
public interface ManagedTemporary extends Closeable {

  @Nonnull
  ByteSink asByteSink();

  @Nonnull
  ByteSource asByteSource();

  boolean isInMemory();

  long size();
}
