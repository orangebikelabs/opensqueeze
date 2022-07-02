/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;
import com.orangebikelabs.orangesqueeze.net.StreamingConnection.Subscription;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class ServerStatusSubscription extends Subscription {
    public ServerStatusSubscription(StreamingConnection connection) {
        super(connection);
    }

    @Override
    public void onSuccess(@Nullable JsonNode node) {
        OSAssert.assertNotNull(node, "can't be null");

        SBContext sbContext = mConnection.getSbContext();
        ServerStatus serverStatus = sbContext.getServerStatus();

        JsonNode dataNode = node.get("data");

        if (dataNode == null) {
            Reporting.report(null, "No data element in server status update", node);
            return;
        }

        List<PlayerId> currentPlayers = new ArrayList<>();
        List<PlayerId> removedPlayers = new ArrayList<>();

        serverStatus.set(dataNode, currentPlayers, removedPlayers);

        // for each player currently in list, add a player subscription if it's not already added
        for (PlayerId id : currentPlayers) {
            mConnection.addPlayerSubscription(id);
        }

        // any removed players? unsubscribe from updates.
        for (PlayerId id : removedPlayers) {
            mConnection.removePlayerSubscription(id);
        }

        // at this point, we've got enough data about things that we consider
        // the connection complete
        mConnection.notifyConnectionSuccess();
    }
}
