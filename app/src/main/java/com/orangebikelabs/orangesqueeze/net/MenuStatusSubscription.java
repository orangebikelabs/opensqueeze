/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerMenus;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.ServerStatus.Transaction;
import com.orangebikelabs.orangesqueeze.net.StreamingConnection.Subscription;

import javax.annotation.Nullable;

/**
 * Standard handler for menu updates received through Comet. The updates are applied on demand to the mutable playerstatus objects, which
 * then eventually persist them to the database.
 *
 * @author tsandee
 */
public class MenuStatusSubscription extends Subscription {
    public MenuStatusSubscription(StreamingConnection connection) {
        super(connection);
    }

    @Override
    public void onSuccess(@Nullable JsonNode response) {
        OSAssert.assertNotNull(response, "can't be null");

        JsonNode dataNode = response.path("data");
        if (dataNode.isArray() && dataNode.size() >= 4) {
            if (!dataNode.path(0).asText().equals("menustatus")) {
                OSLog.w(Tag.DEFAULT, "Unexpected menustatus data", dataNode);
                return;
            }
            JsonNode changedMenu = dataNode.path(1);
            String operation = dataNode.path(2).asText();
            PlayerId playerId = PlayerId.newInstance(dataNode.path(3).asText());

            if (playerId != null) {
                SBContext sbContext = mConnection.getSbContext();
                Transaction transaction = sbContext.getServerStatus().newTransaction();
                try {
                    PlayerMenus menus = sbContext.getPlayerMenus(playerId);
                    transaction.add(menus.withUpdate(operation, changedMenu));
                    transaction.markSuccess();
                } finally {
                    transaction.close();
                }
            }
        }
    }
}
