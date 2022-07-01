/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.common.event.PlayerBrowseMenuChangedEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nonnull;

/**
 * Container for player menus map.
 */
public class PlayerMenusContainer {
    final private ConcurrentMap<PlayerId, PlayerMenus> mMenuMap = new ConcurrentHashMap<>();

    public PlayerMenusContainer() {
        // blank
    }

    /**
     * get current player menus
     */
    @Nonnull
    public PlayerMenus getInstance(PlayerId playerId) {
        PlayerMenus retval = mMenuMap.get(playerId);
        if (retval == null) {
            retval = new PlayerMenus(playerId, Collections.emptyMap(), ImmutableList.of(), true);
            PlayerMenus p = mMenuMap.putIfAbsent(playerId, retval);
            if (p != null) {
                retval = p;
            }
        }
        return retval;
    }

    /**
     * purge any unused menus
     */
    public void setPlayerList(Set<PlayerId> playerIdList) {
        Set<PlayerId> temp = new HashSet<>(mMenuMap.keySet());
        temp.removeAll(playerIdList);
        for (PlayerId id : temp) {
            mMenuMap.remove(id);
        }
    }

    /**
     * @param menus updates menus
     */
    public void commitUpdates(List<PlayerMenus> menus) {
        for (PlayerMenus m : menus) {
            mMenuMap.put(m.getId(), m);

            BusProvider.getInstance().post(new PlayerBrowseMenuChangedEvent(m.getId()));
        }
    }
}
