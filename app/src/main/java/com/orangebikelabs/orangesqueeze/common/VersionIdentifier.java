/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.base.Objects;

import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Simple class to deal with version strings that are strictly numeric
 *
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
@Immutable
public class VersionIdentifier implements Comparable<VersionIdentifier> {

    final static private int MINIMUM_COMPONENTS = 3;

    @Nonnull
    final private int[] mComponents;

    @Nonnull
    final private String mStringValue;

    public VersionIdentifier( String src) {
        String[] stringComponents = src.split("[.\\-]");
        mComponents = new int[Math.max(MINIMUM_COMPONENTS, stringComponents.length)];
        for (int i = 0; i < stringComponents.length; i++) {
            try {
                mComponents[i] = Integer.parseInt(stringComponents[i]);
            } catch (NumberFormatException e) {
                // ignore quietly
            }
        }
        mStringValue = src;
    }

    public int getMajor() {
        return mComponents[0];
    }

    public int getMinor() {
        return mComponents[1];
    }

    public int getMicro() {
        return mComponents[2];
    }

    @Override
    @Nonnull
    public String toString() {
        return mStringValue;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mComponents, mStringValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof VersionIdentifier)) {
            return false;
        }
        VersionIdentifier other = (VersionIdentifier) obj;
        return Arrays.equals(mComponents, other.mComponents) && Objects.equal(mStringValue, other.mStringValue);
    }

    @Override
    public int compareTo(VersionIdentifier another) {
        int maxComponents = Math.max(mComponents.length, another.mComponents.length);

        for (int i = 0; i < maxComponents; i++) {
            int t1, t2;
            t1 = t2 = 0;
            if (i < mComponents.length) {
                t1 = mComponents[i];
            }
            if (i < another.mComponents.length) {
                t2 = another.mComponents[i];
            }
            int diff = Integer.compare(t1, t2);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    public int compareTo(String another) {
        return compareTo(new VersionIdentifier(another));
    }
}
