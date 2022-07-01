/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
