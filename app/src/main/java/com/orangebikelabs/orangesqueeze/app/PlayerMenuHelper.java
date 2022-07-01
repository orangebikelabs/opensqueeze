/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;


import androidx.annotation.Keep;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerMenus;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides simple access to the stored player menu lists in the database.
 *
 * @author tbsandee@orangebikelabs.com
 */
@Keep
public class PlayerMenuHelper {

    /**
     * load the list of root menu ID's that should be displayed, reflecting any PINNED items.
     */
    @Nonnull
    static public List<String> loadRootMenuNodes(@Nullable byte[] smileOrJson) {
        List<String> retval = null;
        if (smileOrJson != null) {
            try {
                ObjectReader or = JsonHelper.getJsonObjectReader().forType(new TypeReference<List<String>>() {/* no overrides */
                });
                retval = or.readValue(JsonHelper.createParserForData(ByteSource.wrap(smileOrJson)));
            } catch (IOException e) {
                Reporting.report(e, "Error loading stored root nodes", null);
            }
        }
        if (retval == null) {
            retval = Collections.emptyList();
        }
        return retval;
    }

    /**
     * return list of all cached player menu sets from the json/smile data
     */
    @Nonnull
    static public Map<PlayerId, PlayerMenuSet> loadPlayerMenus(@Nullable byte[] smileOrJson) {
        OSAssert.assertNotMainThread();

        Map<PlayerId, PlayerMenuSet> retval = null;
        if (smileOrJson != null) {
            try {
                ObjectReader or = JsonHelper.getJsonObjectReader().forType(new TypeReference<Map<PlayerId, PlayerMenuSet>>() {/* no overrides */
                });
                retval = or.readValue(JsonHelper.createParserForData(ByteSource.wrap(smileOrJson)));
            } catch (IOException e) {
                OSLog.w("Error loading stored player menus", e);
            }
        }
        if (retval == null) {
            retval = Maps.newLinkedHashMap();
        }
        return retval;
    }

    final static private AtomicReference<Future<?>> sLastFuture = new AtomicReference<>(null);

    /**
     * used to trigger the database call to store the player menus and menu ordering
     */
    static public void triggerStoreMenus(long time, TimeUnit tu) {
        // create new runnable

        Future<?> newFuture = OSExecutors.getSingleThreadScheduledExecutor().schedule(new StorePlayersMenusCallable(), time, tu);
        Future<?> oldFuture = sLastFuture.getAndSet(newFuture);

        if (oldFuture != null) {
            oldFuture.cancel(false);
        }
    }

    static class StorePlayersMenusCallable implements Callable<Void> {

        @Override
        public Void call() {
            SBContext sbContext = SBContextProvider.get();
            Map<PlayerId, PlayerMenuSet> playerMenuMap = new HashMap<>();
            for (PlayerId id : sbContext.getServerStatus().getAvailablePlayerIds()) {
                // don't trigger a menu update just to write the stored menus
                PlayerMenus menus = sbContext.getPlayerMenus(id);
                PlayerMenuSet set = new PlayerMenuSet(menus.getRootMenusWithoutTrigger());
                if (set.menuSet.size() > 1) {
                    playerMenuMap.put(id, set);
                }
            }

            DatabaseAccess.getInstance(sbContext.getApplicationContext())
                    .getServerQueries()
                    .updateMenus(playerMenuMap, sbContext.getRootBrowseNodes(), sbContext.getServerId());

            return null;
        }
    }

    @Keep
    public static class PlayerMenuSet {
        public PlayerMenuSet(Collection<MenuElement> elements) {
            menuSet = elements;
        }

        public PlayerMenuSet() {
        }

        // must remain public, non-null because we serialize this to JSON
        public Collection<MenuElement> menuSet;

        @Nonnull
        public Map<String, MenuElement> getMenuMap() {
            Map<String, MenuElement> menuMap = new HashMap<>();
            for (MenuElement elem : menuSet) {
                menuMap.put(elem.getId(), elem);
            }
            return menuMap;
        }
    }
}
