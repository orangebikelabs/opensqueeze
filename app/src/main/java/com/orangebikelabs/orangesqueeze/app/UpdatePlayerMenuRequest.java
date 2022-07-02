/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerMenus;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;
import com.orangebikelabs.orangesqueeze.common.ServerStatus.Transaction;
import com.orangebikelabs.orangesqueeze.common.SimpleLoopingRequest;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class UpdatePlayerMenuRequest extends SimpleLoopingRequest {
    // linked hash map preserves item ordering
    final private LinkedHashMap<String, MenuElement> mElements = Maps.newLinkedHashMap();

    public UpdatePlayerMenuRequest(PlayerId playerId) {
        super(playerId);

        // get the entire list in one request, the user doesn't see the results until fully loaded anyways
        setInitialBatchSize(-1);
        setCommands("menu");
        addParameter("direct", "1");
    }

    @Nonnull
    @Override
    public PlayerId getPlayerId() {
        //noinspection ConstantConditions
        return super.getPlayerId();
    }

    @Override
    protected void finalizeRequest() throws SBRequestException {
        super.finalizeRequest();

        if (mElements.size() == 1) {
            OSLog.d("Ignoring empty or placeholder root menu items");
            mElements.clear();
        }

        SBContext sbContext = SBContextProvider.get();
        ServerStatus serverStatus = sbContext.getServerStatus();
        Transaction transaction = serverStatus.newTransaction();
        try {
            PlayerMenus menus = sbContext.getPlayerMenus(getPlayerId());
            transaction.add(menus.withUpdates(mElements, false));

            transaction.markSuccess();
        } finally {
            transaction.close();
        }

        PlayerMenuHelper.triggerStoreMenus(10, TimeUnit.SECONDS);
    }

    @Override
    protected void onLoopItem(SBResult loopingResult, ObjectNode item) throws SBRequestException {
        super.onLoopItem(loopingResult, item);

        try {
            MenuElement elem = MenuElement.get(item, null);
            mElements.put(elem.getId(), elem);
        } catch (IOException e) {
            Reporting.report(e, "Error deserializing menu item", item);
        }
    }
}
