/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.UpdatePlayerMenuRequest;
import com.orangebikelabs.orangesqueeze.browse.node.NodeItem;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

/**
 * @author tsandee
 */
@Immutable
public class PlayerMenus {

    public static class MenuUpdate {
        final String mOperation;
        final JsonNode mUpdateJson;

        public MenuUpdate(String operation, JsonNode node) {
            mOperation = operation;
            mUpdateJson = node;
        }
    }

    final private PlayerId mPlayerId;
    final private boolean mRootMenusCached;
    final private ImmutableList<MenuUpdate> mStoredUpdates;
    final private ImmutableMap<String, MenuElement> mMenus;

    final private AtomicBoolean mMenuLoadTriggered = new AtomicBoolean(false);

    PlayerMenus(PlayerId playerId, Map<String, MenuElement> menus, List<MenuUpdate> storedUpdates, boolean fromCache) {
        mPlayerId = playerId;
        mRootMenusCached = fromCache;
        mStoredUpdates = ImmutableList.copyOf(storedUpdates);

        boolean foundExtras = menus.containsKey(NodeItem.EXTRAS_NODE);
        if (!foundExtras) {
            boolean needExtras = false;
            for (MenuElement e : menus.values()) {
                String node = e.getNode();

                if (NodeItem.EXTRAS_NODE.equals(node)) {
                    needExtras = true;
                    break;
                }
            }

            if (needExtras) {
                MenuElement elem = new MenuElement();
                elem.setIsANode(true);
                elem.setId(NodeItem.EXTRAS_NODE);
                elem.setNode(NodeItem.HOME_NODE);
                elem.setText(SBContextProvider.get().getApplicationContext().getString(R.string.extras_node_text));
                elem.setWeight(50.0d);

                menus = new HashMap<>(menus);
                menus.put(elem.getId(), elem);
            }
        }

        mMenus = ImmutableMap.copyOf(menus);
    }

    @Nonnull
    public PlayerId getId() {
        return mPlayerId;
    }

    @Nonnull
    public PlayerMenus withUpdates(Map<String, MenuElement> rootMenus, boolean fromCache) {
        if (!mMenus.isEmpty() && fromCache) {
            // ignore case where these are coming from the cache and we've already got menus
            return this;
        }

        Map<String, MenuElement> buildMap = new HashMap<>(rootMenus);
        List<MenuUpdate> updates = mStoredUpdates;
        if (!updates.isEmpty()) {
            applyMenuUpdates(buildMap, updates);
        }
        return new PlayerMenus(mPlayerId, buildMap, ImmutableList.of(), fromCache);
    }

    @Nonnull
    public PlayerMenus withUpdate(String operation, JsonNode menu) {
        if (mRootMenusCached) {
            List<MenuUpdate> newUpdateList = new ArrayList<>(mStoredUpdates);
            newUpdateList.add(new MenuUpdate(operation, menu));
            return new PlayerMenus(mPlayerId, mMenus, newUpdateList, true);
        } else {
            Map<String, MenuElement> mutable = new HashMap<>(mMenus);
            applyMenuUpdates(mutable, Collections.singletonList(new MenuUpdate(operation, menu)));
            return new PlayerMenus(mPlayerId, mutable, ImmutableList.of(), false);
        }
    }

    @Nonnull
    public ImmutableCollection<MenuElement> getRootMenusWithoutTrigger() {
        return mMenus.values();
    }

    synchronized public void triggerReload() {
        if (mMenuLoadTriggered.compareAndSet(false, true)) {
            // first time we request the root menus, kick off an update request
            UpdatePlayerMenuRequest request = new UpdatePlayerMenuRequest(mPlayerId);
            ListenableFuture<?> future = request.submit(OSExecutors.getUnboundedPool());
            future.addListener(() -> mMenuLoadTriggered.set(false), MoreExecutors.newDirectExecutorService());
        }
    }

    @Nonnull
    synchronized public ImmutableCollection<MenuElement> getRootMenus() {
        if (mRootMenusCached) {
            triggerReload();
        }
        return getRootMenusWithoutTrigger();
    }

    private void applyMenuUpdates(Map<String, MenuElement> map, List<MenuUpdate> menuUpdateList) {
        int modCount = 0;
        for (MenuUpdate menuUpdate : menuUpdateList) {
            try {
                if (Objects.equal(menuUpdate.mOperation, "add")) {
                    List<MenuElement> newElements = JsonHelper.getJsonObjectReader().forType(new TypeReference<List<MenuElement>>() {
                    })
                            .readValue(menuUpdate.mUpdateJson.traverse());

                    for (MenuElement elem : newElements) {
                        map.put(elem.getId(), elem);
                        modCount++;
                    }
                    OSLog.d("Added " + modCount + " menu items");
                } else if (Objects.equal(menuUpdate.mOperation, "remove")) {
                    for (JsonNode idMap : menuUpdate.mUpdateJson) {
                        String id = idMap.path("id").asText();
                        map.remove(id);
                        modCount++;
                    }
                    if (modCount > 0) {
                        OSLog.d("Removed " + modCount + " menu items");
                    }
                } else {
                    Reporting.report(null, "Unsupported menu update", menuUpdate);
                }
            } catch (IOException e) {
                Reporting.report(e, "Error processing menu update", menuUpdate);
            }
        }
    }
}
