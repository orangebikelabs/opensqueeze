/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tsandee
 */
@ThreadSafe
public class DownloadJob implements Iterable<DownloadTrack> {
    // synchronized so that we can return and expose iterable on demand without worrying about making a copy
    @GuardedBy("mElements")
    final private Set<DownloadTrack> mElements = Collections.synchronizedSet(new TreeSet<>());

    @GuardedBy("this")
    private int mPosition;

    @GuardedBy("this")
    private int mMax;

    public DownloadJob() {
    }

    public int addTracks(Collection<DownloadTrack> tracks) {
        mElements.addAll(tracks);
        return tracks.size();
    }

    public synchronized void setProgress(int pos, int max) {
        mPosition = pos;
        mMax = max;
    }

    public synchronized boolean isLoadComplete() {
        return mPosition >= mMax;
    }

    public synchronized int getPosition() {
        return mPosition;
    }

    public synchronized int getMax() {
        return mMax;
    }

    @Nonnull
    @Override
    public Iterator<DownloadTrack> iterator() {
        return mElements.iterator();
    }
}
