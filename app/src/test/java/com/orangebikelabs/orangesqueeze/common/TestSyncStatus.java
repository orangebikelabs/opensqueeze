/*
 * Copyright (c) 2014-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author tsandee
 */
public class TestSyncStatus {

    private SyncStatus mSyncStatus;
    final private PlayerId[] mPlayers = new PlayerId[10];


    @Before
    public void beforeTest() {
        SetMultimap<Integer, PlayerId> init = HashMultimap.create();
        for (int i = 0; i < mPlayers.length; i++) {
            mPlayers[i] = new PlayerId("player" + i);
        }

        init.put(1, mPlayers[0]);
        init.put(1, mPlayers[1]);
        init.put(1, mPlayers[2]);

        init.put(2, mPlayers[3]);
        init.put(2, mPlayers[4]);

        mSyncStatus = new SyncStatus(init);
    }


    /**
     * this tests the initial sync group setup
     */
    @Test
    public void testInitialGroupMemberships() {
        assertThat(mSyncStatus.getSyncGroup(mPlayers[0]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[2]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[3]), is(Optional.of(2)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4]), is(Optional.of(2)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[5]), is(Optional.<Integer>absent()));
    }

    /**
     * tests unsynchronize behavior
     */
    @Test
    public void testUnsynchronize() {
        assertThat(mSyncStatus.unsynchronize(mPlayers[3]), is(true));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[3]), is(Optional.<Integer>absent()));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4]), is(Optional.<Integer>absent()));
    }

    /**
     * tests unsynchronize behavior when invalid playerid supplied
     */
    @Test
    public void testUnsynchronizeInvalid() {
        assertThat(mSyncStatus.unsynchronize(mPlayers[5]), is(false));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[5]), is(Optional.<Integer>absent()));
    }


    /**
     * tests synchronize behavior
     */
    @Test
    public void testSynchronizeNoop() {
        assertThat(mSyncStatus.synchronize(mPlayers[4], mPlayers[3]), is(false));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[3]), is(Optional.of(2)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4]), is(Optional.of(2)));
    }

    /**
     * tests synchronize behavior
     */
    @Test
    public void testSynchronizeChangeGroup() {
        assertThat(mSyncStatus.synchronize(mPlayers[3], mPlayers[1]), is(true));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[3]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4]), is(Optional.<Integer>absent()));
    }


    /**
     * tests synchronize behavior
     */
    @Test
    public void testSynchronizeNewGroup() {
        assertThat(mSyncStatus.synchronize(mPlayers[5], mPlayers[6]), is(true));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[5]), is(Optional.of(3)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[6]), is(Optional.of(3)));
    }

    /**
     * tests group repacking behavior
     */
    @Test
    public void testRepack() {
        assertThat(mSyncStatus.unsynchronize(mPlayers[0]), is(true));
        assertThat(mSyncStatus.unsynchronize(mPlayers[1]), is(true));
        assertThat(mSyncStatus.unsynchronize(mPlayers[2]), is(false));

        assertThat(mSyncStatus.getSyncGroup(mPlayers[0]), is(Optional.<Integer>absent()));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1]), is(Optional.<Integer>absent()));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[2]), is(Optional.<Integer>absent()));

        assertThat(mSyncStatus.getSyncGroup(mPlayers[3]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4]), is(Optional.of(1)));
    }

    /**
     * tests status update-style changes
     */
    @Test
    public void testStatusUpdateNoop() {
        List<List<PlayerId>> updates = new ArrayList<>();
        updates.add(Arrays.asList(mPlayers[0], mPlayers[1], mPlayers[2]));

        assertThat(mSyncStatus.updateSyncStatus(updates), is(false));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[0]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[2]), is(Optional.of(1)));
    }

    /**
     * tests status update-style changes
     */
    @Test
    public void testStatusUpdateRemoveOne() {
        List<List<PlayerId>> updates = new ArrayList<>();
        updates.add(Arrays.asList(mPlayers[0], mPlayers[1]));

        assertThat(mSyncStatus.updateSyncStatus(updates), is(true));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[0]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[2]), is(Optional.<Integer>absent()));
    }

    /**
     * tests status update-style changes
     */
    @Test
    public void testStatusMultiChange() {
        List<List<PlayerId>> updates = new ArrayList<>();
        updates.add(Arrays.asList(mPlayers[0], mPlayers[1]));
        updates.add(Arrays.asList(mPlayers[2], mPlayers[8]));

        assertThat(mSyncStatus.updateSyncStatus(updates), is(true));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[0]), is(Optional.of(1)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1]), is(Optional.of(1)));

        assertThat(mSyncStatus.getSyncGroup(mPlayers[2]), is(Optional.of(3)));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[8]), is(Optional.of(3)));
    }
}
