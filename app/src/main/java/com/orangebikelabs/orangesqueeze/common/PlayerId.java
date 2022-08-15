/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.Objects;

import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Opaque representation of a player ID to keep APIs safer to use.
 *
 * @author tbsandee@orangebikelabs.com
 */
@Immutable
@ThreadSafe
public class PlayerId implements Parcelable, Serializable, Comparable<PlayerId> {


    @Nullable
    static public PlayerId newInstance(@Nullable String value) {
        if (value == null || value.equals("")) {
            return null;
        } else {
            return new PlayerId(value);
        }
    }

    public static final Parcelable.Creator<PlayerId> CREATOR = new Parcelable.Creator<>() {
        @Override
        public PlayerId createFromParcel(Parcel in) {
            return new PlayerId(in.readString());
        }

        @Override
        public PlayerId[] newArray(int size) {
            return new PlayerId[size];
        }
    };

    final private String mId;

    public PlayerId(String id) {
        OSAssert.assertNotNull(id, "null player id not allowed");
        mId = id;
    }

    @Override
    @Nonnull
    public String toString() {
        return mId;
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
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
        PlayerId other = (PlayerId) obj;
        return Objects.equal(mId, other.mId);
    }

    @Override
    public int compareTo(PlayerId another) {
        return mId.compareTo(another.mId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
    }
}
