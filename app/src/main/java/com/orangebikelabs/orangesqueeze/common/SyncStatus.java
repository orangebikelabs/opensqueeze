/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tsandee
 */
@Keep
@ThreadSafe
public class SyncStatus {

    @GuardedBy("this")
    final private SetMultimap<Integer, PlayerId> mGroups;

    @GuardedBy("this")
    final private Map<PlayerId, Integer> mPlayers;

    public SyncStatus() {
        this(HashMultimap.create());
    }

    SyncStatus(SetMultimap<Integer, PlayerId> groups) {
        mGroups = groups;

        mPlayers = new HashMap<>();
        for (Map.Entry<Integer, PlayerId> entry : groups.entries()) {
            mPlayers.put(entry.getValue(), entry.getKey());
        }
    }

    @Nonnull
    synchronized public Optional<Integer> getSyncGroup(PlayerId playerId) {
        return Optional.ofNullable(mPlayers.get(playerId));
    }

    @Nonnull
    synchronized public SyncStatus getReadonlySnapshot() {
        return new SyncStatus(ImmutableSetMultimap.copyOf(mGroups));
    }

    /**
     * receive sync updates, a list of sync group lists
     */
    synchronized public boolean updateSyncStatus(List<List<PlayerId>> syncGroups) {
        boolean retval = false;

        for (List<PlayerId> group : syncGroups) {
            if (group.isEmpty()) {
                // empty group? weird. skip it.
                continue;
            }

            // first, for this changeset do we need to update the sync groups at all?
            boolean changed;

            Integer currentSyncGroup = getSyncGroup(group.get(0)).orElse(null);
            if (currentSyncGroup == null && group.size() == 1) {
                changed = false;
            } else if (currentSyncGroup == null) {
                // "master" isn't in a group at all, force a readjustment
                changed = true;
            } else {
                // does the list of players match our expected list?
                Set<PlayerId> currentGroupSet = mGroups.get(currentSyncGroup);
                changed = !currentGroupSet.equals(new HashSet<>(group));
            }

            if (changed) {
                retval = true;
                processSyncGroup(group);
            }
        }
        return retval;
    }

    synchronized public boolean unsynchronize(PlayerId playerId) {
        boolean retval = false;
        Integer group = mPlayers.remove(playerId);
        if (group != null) {
            mGroups.remove(group, playerId);

            retval = true;

            removeSoloGroups();
            repackGroups();
        }
        return retval;
    }

    synchronized public boolean synchronize(PlayerId playerId, PlayerId targetPlayerId) {
        // determine if targetPlayerId has a group
        Integer newGroup = mPlayers.get(targetPlayerId);

        // get new group membership
        Integer oldGroup = mPlayers.get(playerId);

        if (newGroup != null && Objects.equal(newGroup, oldGroup)) {
            // the groups don't change
            return false;
        }

        if (oldGroup != null) {
            // remove from that oldgroup
            mGroups.remove(oldGroup, playerId);
        }

        if (newGroup == null) {
            // it doesn't, create one
            newGroup = getNextGroupId();
            mPlayers.put(targetPlayerId, newGroup);
            mGroups.put(newGroup, targetPlayerId);
        }

        // finally, add playerid to that group
        mPlayers.put(playerId, newGroup);
        mGroups.put(newGroup, playerId);

        removeSoloGroups();
        repackGroups();

        return true;
    }

    @Override
    @Nonnull
    synchronized public String toString() {
        return MoreObjects.toStringHelper(this).add("groups", mGroups).add("players", mPlayers).toString();
    }

    private void processSyncGroup(List<PlayerId> groupMembers) {
        // first, remove all of these players from groups
        for (PlayerId p : groupMembers) {
            Integer val = mPlayers.remove(p);
            if (val != null) {
                mGroups.remove(val, p);
            }
        }

        removeSoloGroups();

        // finally, re-add the groups
        if (groupMembers.size() > 1) {
            Integer newGroupId = getNextGroupId();
            mGroups.putAll(newGroupId, groupMembers);
            for (PlayerId id : groupMembers) {
                mPlayers.put(id, newGroupId);
            }
        }

        repackGroups();
    }

    private void removeSoloGroups() {
        // create working copy since keyset will change when we remove keys
        Set<Integer> groups = new HashSet<>(mGroups.keySet());

        // look for any groups with single members, and remove them
        for (Integer group : groups) {
            Set<PlayerId> players = mGroups.get(group);
            if (players.size() == 1) {
                // first remove the player assignment because mGroups.removeAll will empty this set
                for (PlayerId removeId : players) {
                    mPlayers.remove(removeId);
                }
                // then remove the group membership
                mGroups.removeAll(group);
            }
        }
    }

    private void repackGroups() {
        // look for gaps in group numbers and fill them
        int newVal = 1;
        TreeSet<Integer> oldGroups = new TreeSet<>(mGroups.keySet());
        while (!oldGroups.isEmpty()) {
            if (!oldGroups.remove(newVal)) {
                // newval not found, we have a gap
                Integer oldVal = oldGroups.first();
                oldGroups.remove(oldVal);

                Set<PlayerId> players = mGroups.get(oldVal);
                for (PlayerId id : players) {
                    mPlayers.put(id, newVal);
                }
                mGroups.putAll(newVal, players);
                mGroups.removeAll(oldVal);
            }

            newVal++;
        }
    }

    @Nonnull
    private Integer getNextGroupId() {
        int i = 1;
        while (true) {
            if (!mGroups.containsKey(i)) {
                return i;
            }
            i++;
        }
    }
}
