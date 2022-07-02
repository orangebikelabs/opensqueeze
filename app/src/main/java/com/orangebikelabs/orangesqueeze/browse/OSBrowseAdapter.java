/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse;

import android.widget.Adapter;

import com.orangebikelabs.orangesqueeze.browse.common.Item;

import java.util.Collection;

/**
 * @author tsandee
 */
public interface OSBrowseAdapter extends Adapter {

  void clear();

  @Override
  Item getItem(int pos);

  void notifyDataSetChanged();

  void setNotifyOnChange(boolean notify);

  void addAll(Collection<? extends Item> itemList);

  void add(Item item);

  void setSorted(boolean sorted);
}
