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

import static com.google.common.truth.Truth.assertThat;

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
        assertThat(mSyncStatus.getSyncGroup(mPlayers[0])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[2])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[3])).isEqualTo(Optional.of(2));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4])).isEqualTo(Optional.of(2));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[5])).isEqualTo(Optional.<Integer>absent());
    }

    /**
     * tests unsynchronize behavior
     */
    @Test
    public void testUnsynchronize() {
        assertThat(mSyncStatus.unsynchronize(mPlayers[3])).isTrue();
        assertThat(mSyncStatus.getSyncGroup(mPlayers[3])).isEqualTo(Optional.<Integer>absent());
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4])).isEqualTo(Optional.<Integer>absent());
    }

    /**
     * tests unsynchronize behavior when invalid playerid supplied
     */
    @Test
    public void testUnsynchronizeInvalid() {
        assertThat(mSyncStatus.unsynchronize(mPlayers[5])).isFalse();
        assertThat(mSyncStatus.getSyncGroup(mPlayers[5])).isEqualTo(Optional.<Integer>absent());
    }


    /**
     * tests synchronize behavior
     */
    @Test
    public void testSynchronizeNoop() {
        assertThat(mSyncStatus.synchronize(mPlayers[4], mPlayers[3])).isFalse();
        assertThat(mSyncStatus.getSyncGroup(mPlayers[3])).isEqualTo(Optional.of(2));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4])).isEqualTo(Optional.of(2));
    }

    /**
     * tests synchronize behavior
     */
    @Test
    public void testSynchronizeChangeGroup() {
        assertThat(mSyncStatus.synchronize(mPlayers[3], mPlayers[1])).isEqualTo(true);
        assertThat(mSyncStatus.getSyncGroup(mPlayers[3])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4])).isEqualTo(Optional.<Integer>absent());
    }


    /**
     * tests synchronize behavior
     */
    @Test
    public void testSynchronizeNewGroup() {
        assertThat(mSyncStatus.synchronize(mPlayers[5], mPlayers[6])).isEqualTo(true);
        assertThat(mSyncStatus.getSyncGroup(mPlayers[5])).isEqualTo(Optional.of(3));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[6])).isEqualTo(Optional.of(3));
    }

    /**
     * tests group repacking behavior
     */
    @Test
    public void testRepack() {
        assertThat(mSyncStatus.unsynchronize(mPlayers[0])).isTrue();
        assertThat(mSyncStatus.unsynchronize(mPlayers[1])).isTrue();
        assertThat(mSyncStatus.unsynchronize(mPlayers[2])).isFalse();

        assertThat(mSyncStatus.getSyncGroup(mPlayers[0])).isEqualTo(Optional.<Integer>absent());
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1])).isEqualTo(Optional.<Integer>absent());
        assertThat(mSyncStatus.getSyncGroup(mPlayers[2])).isEqualTo(Optional.<Integer>absent());

        assertThat(mSyncStatus.getSyncGroup(mPlayers[3])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[4])).isEqualTo(Optional.of(1));
    }

    /**
     * tests status update-style changes
     */
    @Test
    public void testStatusUpdateNoop() {
        List<List<PlayerId>> updates = new ArrayList<>();
        updates.add(Arrays.asList(mPlayers[0], mPlayers[1], mPlayers[2]));

        assertThat(mSyncStatus.updateSyncStatus(updates)).isFalse();
        assertThat(mSyncStatus.getSyncGroup(mPlayers[0])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[2])).isEqualTo(Optional.of(1));
    }

    /**
     * tests status update-style changes
     */
    @Test
    public void testStatusUpdateRemoveOne() {
        List<List<PlayerId>> updates = new ArrayList<>();
        updates.add(Arrays.asList(mPlayers[0], mPlayers[1]));

        assertThat(mSyncStatus.updateSyncStatus(updates)).isTrue();
        assertThat(mSyncStatus.getSyncGroup(mPlayers[0])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[2])).isEqualTo(Optional.<Integer>absent());
    }

    /**
     * tests status update-style changes
     */
    @Test
    public void testStatusMultiChange() {
        List<List<PlayerId>> updates = new ArrayList<>();
        updates.add(Arrays.asList(mPlayers[0], mPlayers[1]));
        updates.add(Arrays.asList(mPlayers[2], mPlayers[8]));

        assertThat(mSyncStatus.updateSyncStatus(updates)).isTrue();
        assertThat(mSyncStatus.getSyncGroup(mPlayers[0])).isEqualTo(Optional.of(1));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[1])).isEqualTo(Optional.of(1));

        assertThat(mSyncStatus.getSyncGroup(mPlayers[2])).isEqualTo(Optional.of(3));
        assertThat(mSyncStatus.getSyncGroup(mPlayers[8])).isEqualTo(Optional.of(3));
    }
}
