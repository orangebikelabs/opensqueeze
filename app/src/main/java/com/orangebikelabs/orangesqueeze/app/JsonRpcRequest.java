/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.content.Context;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.net.HttpUtils;
import com.orangebikelabs.orangesqueeze.net.JsonRpc;
import com.orangebikelabs.orangesqueeze.net.PlayerStatusSubscription;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
public class JsonRpcRequest extends AbsRequest {
    final private Context mContext;
    final private SBContext mSbContext;

    public JsonRpcRequest(Context context, SBContext sbContext, List<?> commands) {
        super(commands);
        mContext = context;
        mSbContext = sbContext;
    }

    @Nonnull
    @Override
    public SBResult call() throws SBRequestException, InterruptedException {
        if (isCacheable()) {
            OSLog.w("Cacheable request called using uncacheable JSON-RPC: " + this);
        }
        OSAssert.assertNotMainThread();
        if (!mSbContext.awaitConnection("JsonRpcRequest " + getCommands())) {
            throw new SBRequestException(mContext.getString(R.string.exception_connection_timeout));
        }
        ObjectNode request = JsonHelper.getJsonObjectMapper().createObjectNode();
        request.put("id", Integer.valueOf(1));
        request.put("method", "slim.request");

        ArrayNode params = JsonHelper.getJsonObjectMapper().createArrayNode();

        PlayerId playerId = getPlayerId();
        params.add(playerId != null ? playerId.toString() : "");
        params.add(prepareCommands());

        request.set("params", params);

        try {
            URL url = HttpUtils.getJSONUrl(mSbContext.getConnectionInfo());
            JsonRpc rpc = new JsonRpc();
            rpc.setUseGlobalCookies(true);
            // use credentials from the supplied context
            rpc.setCredentials(mSbContext.getConnectionCredentials());
            rpc.setTimeout(getTimeoutMillis(), TimeUnit.MILLISECONDS);
            JsonRpc.Result rpcResult = rpc.execute(url, request);
            try {
                JsonParser parser = rpcResult.getParser();
                JsonToken token = parser.nextToken();
                if (token == JsonToken.START_OBJECT) {
                    JsonNode completeResult = parser.readValueAsTree();
                    JsonNode result = completeResult.get("result");
                    if (result != null && result.isObject()) {
                        SimpleResult retval = new SimpleResult(result);
                        if (getCommitType() == CommitType.PLAYERUPDATE && playerId != null) {
                            // when playerid gets update, commit this result
                            PlayerStatusSubscription.registerCommittableResult(playerId, retval);
                        } else if (getCommitType() == CommitType.IMMEDIATE) {
                            retval.commit();
                        }
                        return retval;
                    } else {
                        String errorMessage = "No result from server";
                        OSLog.e(Tag.DEFAULT, errorMessage, completeResult);
                        throw new SBRequestException(errorMessage);
                    }
                } else {
                    String error = "Unexpected JSON: token=" + parser.getCurrentToken() + ", name=" + parser.getCurrentName();
                    OSLog.w(error);
                    throw new SBRequestException(error);
                }
            } finally {
                rpcResult.close();
            }
        } catch (IOException e) {
            OSLog.i("Exception connecting to server", e);
            throw SBRequestException.wrap(e);
        }
    }

    protected ArrayNode prepareCommands() {
        ArrayNode retval = JsonHelper.getJsonObjectMapper().createArrayNode();
        for (Object s : mCommands) {
            if (s instanceof Integer) {
                retval.add((Integer) s);
            } else {
                retval.add(s.toString());
            }
        }
        return retval;
    }
}
