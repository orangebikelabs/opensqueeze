/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import androidx.annotation.DrawableRes;
import android.widget.AbsListView;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

abstract public class Item {
    public static final ImmutableList<IconRetriever> sNullIconRetrieverList = ImmutableList.of();
    private static final AtomicLong sItemIdGenerator = new AtomicLong();

    final private long mAdapterItemId = sItemIdGenerator.incrementAndGet();

    public long getAdapterItemId() {
        return mAdapterItemId;
    }

    @Nonnull
    abstract public String getText();

    @Nonnull
    abstract public ItemType getBaseType();

    public boolean isEnabled() {
        return false;
    }

    @Nullable
    public String getSectionName() {
        return null;
    }

    @Nonnull
    public String getItemTitle() {
        String text = getText();

        int ndx = text.indexOf('\n');
        if (ndx == -1) {
            return text;
        } else {
            return text.substring(0, ndx);
        }
    }

    @DrawableRes
    public int getIconRid() {
        return 0;
    }

    @Nonnull
    public JsonNode getNode() {
        return MissingNode.getInstance();
    }

    public void preload(ThumbnailProcessor processor, AbsListView parentView) throws InterruptedException {
        // this is called in a very high-volume situation, avoid creating an iterator

        ImmutableList<IconRetriever> list = getIconRetrieverList();
        int length = list.size();
        for (int i = 0; i < length; i++) {
            IconRetriever ir = list.get(i);
            if (ir.applies(this)) {
                ir.load(processor, this, parentView, null);
                break;
            }
        }
    }

    @Nonnull
    public ImmutableList<IconRetriever> getIconRetrieverList() {
        return sNullIconRetrieverList;
    }

    public boolean isSingleItemConsideredEmpty() {
        return false;
    }

    @Nullable
    public NavigationItem getNavigationItem() {
        return null;
    }

    @Nonnull
    @Override
    public String toString() {
        return getItemTitle();
    }
}
