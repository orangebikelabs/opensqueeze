/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.actions;

import androidx.annotation.DrawableRes;
import androidx.fragment.app.Fragment;

import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
abstract public class AbsAction<T> implements Comparable<AbsAction<T>>, Serializable {
    /**
     * a forever-incrementing long used as a basis for ordering actions when there is no other way to order them.
     */
    final private static AtomicLong sOrder = new AtomicLong(0);

    @DrawableRes
    final private int mIconRid;
    final private String mMenuString;
    final protected long mCreationOrder = sOrder.getAndIncrement();

    protected AbsAction(String menuString, @DrawableRes int iconRid) {
        mIconRid = iconRid;
        mMenuString = menuString;
    }

    @DrawableRes
    public int getIconRid() {
        return mIconRid;
    }

    @Override
    @Nonnull
    public String toString() {
        return mMenuString;
    }

    public boolean isEnabled() {
        return true;
    }

    @Override
    public int compareTo(AbsAction<T> another) {
        return ComparisonChain.start().compare(mCreationOrder, another.mCreationOrder).result();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mIconRid, mMenuString);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AbsAction<?> other = (AbsAction<?>) obj;
        return Objects.equal(mIconRid, other.mIconRid) && Objects.equal(toString(), other.toString());
    }

    /**
     * returns true if the action candidate can be used on this item and initializes it
     */
    abstract public boolean initialize(T item);

    /**
     * execute the command using the supplied controller
     *
     * @param controller the controller
     * @return whether or not to dismiss the action dialog
     */
    abstract public boolean execute(Fragment controller);

}