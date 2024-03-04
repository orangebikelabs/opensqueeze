/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import android.content.Context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Monitor;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.PlayerMenuHelper.PlayerMenuSet;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerMenus;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;
import com.orangebikelabs.orangesqueeze.common.ServerStatus.Transaction;
import com.orangebikelabs.orangesqueeze.net.JsonRpc.Result;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class StreamingConnection extends AbstractExecutionThreadService {
    final private static String MESSAGE_META_HANDSHAKE = "/meta/handshake";
    final private static String MESSAGE_META_CONNECT = "/meta/connect";
    final private static String MESSAGE_META_SUBSCRIBE = "/meta/subscribe";
    final private static String MESSAGE_SLIM_SUBSCRIBE = "/slim/subscribe";
    final private static String MESSAGE_SLIM_REQUEST = "/slim/request";

    final private static String CLIENT_ID = "clientId";
    final private static String CONNECTION_TYPE = "connectionType";
    //final private static String LONG_POLLING = "long-polling";
    final private static String STREAMING = "streaming";
    final private static String SUBSCRIPTION = "subscription";
    final private static String SUCCESSFUL = "successful";
    final private static String ERROR = "error";
    final private static String CHANNEL = "channel";
    final private static String REQUEST = "request";
    final private static String RESPONSE = "response";
    final private static String ID = "id";
    final private static String DATA = "data";

    final private static int PLAYER_STATUS_INTERVAL = 600;
    final private static int SERVER_STATUS_INTERVAL = 60;

    final protected ConcurrentMap<String, Subscription> mSubscriptions = new MapMaker().concurrencyLevel(1).makeMap();
    final protected ConcurrentMap<String, SettableFuture<JsonNode>> mRequests = new MapMaker().concurrencyLevel(1).weakValues().makeMap();
    final protected ConcurrentMap<Integer, SettableFuture<JsonNode>> mRequestsById = new MapMaker().concurrencyLevel(1).weakValues().makeMap();

    /**
     * this is the number of subscriptions requests that are outstanding
     */
    final protected Monitor mSubscriptionCountMonitor = new Monitor();
    final protected Monitor.Guard mSubscriptionCountIsZero = new Monitor.Guard(mSubscriptionCountMonitor) {

        @Override
        public boolean isSatisfied() {
            return mSubscriptionCount == 0;
        }
    };

    @GuardedBy("mSubscriptionMonitor")
    protected int mSubscriptionCount;

    /**
     * normal synchronization lock
     */
    final protected Object mLock = new Object();

    final protected ObjectMapper mObjectMapper;

    final protected String mUUID;

    @Nonnull
    final protected SBContext mSbContext;

    final protected String mMacAddress;

    @Nonnull
    final protected ConnectionInfo mConnectionInfo;

    @Nullable
    final protected SBCredentials mConnectionCredentials;

    @Nonnull
    final protected CountDownLatch mConnectionComplete = new CountDownLatch(1);

    // these values start at 1, because zero indicates no interest in result
    @Nonnull
    final private AtomicInteger mNextSlimRequestId = new AtomicInteger(1);

    // lock on write, read anywhere
    @GuardedBy("mLock")
    volatile private String mClientId;

    @GuardedBy("mSubscriptionCountMonitor")
    final protected LinkedHashMap<PlayerId, PlayerMenuSet> mPlayerMenuPreload = new LinkedHashMap<>();

    final private BlockingQueue<WorkItem> mWorkQueue = new LinkedBlockingQueue<>();
    final private static WorkItem QUIT = new WorkItem(MissingNode.getInstance());

    /**
     * lateinit
     */
    volatile private StreamingSocket mStreamingSocket;

    /**
     * lateinit
     */
    volatile private URL mCometUrl;

    @GuardedBy("mLock")
    final private List<StoredRequest> mStoredRequests = new ArrayList<>();

    /**
     * whether or not our primary connection is established
     */
    @GuardedBy("mLock")
    private boolean mPrimaryConnectionEstablished;

    @GuardedBy("mLock")
    private boolean mSendWakeOnLan;

    @GuardedBy("mLock")
    private boolean mConnectionFailed;

    @GuardedBy("mLock")
    private boolean mShouldRetry;

    @GuardedBy("mLock")
    @Nullable
    private String mConnectionFailureReason;

    public StreamingConnection(SBContext sbContext) {
        mSbContext = sbContext;
        mObjectMapper = JsonHelper.getJsonObjectMapper();
        mUUID = SBPreferences.get().getUUID();
        mMacAddress = NetworkTools.getMacAddress(mSbContext.getApplicationContext());
        mConnectionInfo = sbContext.getConnectionInfo();
        mConnectionCredentials = sbContext.getConnectionCredentials();
    }

    @Override
    protected void startUp() throws IOException {
        mCometUrl = HttpUtils.getCometUrl(mConnectionInfo);
        mStreamingSocket = new StreamingSocket(this);

        if (getSendWakeOnLan()) {
            NetworkTools.sendWakeOnLan(getApplicationContext(), mConnectionInfo);
        }

        // we just expect these to work
        mSubscriptions.put(MESSAGE_META_SUBSCRIBE, mMetaSubscribeSubscription);
        mSubscriptions.put(MESSAGE_SLIM_SUBSCRIBE, mSlimSubscribeSubscription);
        mSubscriptions.put(MESSAGE_SLIM_REQUEST, mNullSubscription);
        mSubscriptions.put(MESSAGE_META_CONNECT, mConnectResponseSubscription);

        // response keeps connection moving forward
        mSubscriptions.put(MESSAGE_META_HANDSHAKE, mMetaHandshakeSubscription);

        sendHandshakeMessage();

        mStreamingSocket.connect();
    }

    @Override
    protected void shutDown() {
        // when the connection shuts down, ensure nobody continues blocking on awaitConnection
        notifyConnectionFailed("shutting down", null);

        mStreamingSocket.close();
    }

    @Override
    protected void run() {
        WorkItem item;
        while (checkRunning()) {
            try {
                item = mWorkQueue.poll(60, TimeUnit.SECONDS);
                if (item == QUIT) {
                    break;
                }
                if (item == null) {
                    OSLog.w(Tag.NETWORK, "Connection appears to be idle, sending dummy request");
                    continue;
                }

                boolean handled = handleResponse(item.mNode);
                if (!handled) {
                    OSLog.w(Tag.NETWORK, "Unhandled response: " + item.mNode);
                }
            } catch (InterruptedException e) {
                OSLog.d(Tag.NETWORK, "Streaming connection run::interrupted");
            }
        }
    }

    synchronized public void setSendWakeOnLan(boolean sendWakeOnLan) {
        mSendWakeOnLan = sendWakeOnLan;
    }

    synchronized public boolean getSendWakeOnLan() {
        return mSendWakeOnLan;
    }

    @Nonnull
    Context getApplicationContext() {
        return mSbContext.getApplicationContext();
    }

    @Nonnull
    SBContext getSbContext() {
        return mSbContext;
    }

    @Override
    protected void triggerShutdown() {
        quitMain(false);
    }

    protected void sendHandshakeMessage() {
        JsonNode request = createHandshakeMessage();
        mStreamingSocket.addRequest(request);
    }

    protected void sendConnectMessage(ArrayNode requests) {
        mStreamingSocket.addRequest(requests);
        mStreamingSocket.addDoneSendingRequest();
    }

    @Nonnull
    public ListenableFuture<JsonNode> submitRequest(long defaultRequestTimeout, TimeUnit defaultRequestTimeoutUnits,
                                                    @Nullable PlayerId playerId, List<Object> commands)
            throws InterruptedException, TimeoutException {

        if (!mConnectionComplete.await(defaultRequestTimeout, defaultRequestTimeoutUnits)) {
            throw new TimeoutException(mSbContext.getApplicationContext().getString(R.string.exception_connection_timeout));
        }

        if (!isRunning()) {
            throw new InterruptedException();
        }

        if (didConnectionFail()) {
            return Futures.immediateFailedFuture(new Exception("Connection Failure: " + getConnectionFailureReason()));
        }

        OSLog.TimingLoggerCompat requestTimer = Tag.TIMING.newTimingLogger("StreamingConnection::submitRequest");
        String response;

        ArrayNode values = mObjectMapper.createArrayNode();

        int length = commands.size();
        for (int i = 0; i < length; i++) {
            Object o = commands.get(i);
            if (o instanceof Integer) {
                values.add((Integer) o);
            } else {
                values.add(o.toString());
            }
        }

        ArrayNode requestArray = mObjectMapper.createArrayNode();
        requestArray.add(playerId != null ? playerId.toString() : "");
        requestArray.add(values);

        int requestId = getNextRequestId();
        response = "/" + getClientId() + "/slim/request";
        ObjectNode request = createSlimRequestMessage(requestArray, requestId, response);

        SettableFuture<JsonNode> retval = SettableFuture.create();

        // look up requests by request key
        mRequests.put(buildRequestKey(requestId, response), retval);

        // also do requests by id so we can get error information
        mRequestsById.put(requestId, retval);

        requestTimer.addSplit("add request");
        addRequest(request, retval);

        requestTimer.close();

        return retval;
    }

    protected boolean checkRunning() {
        boolean retval = isRunning();
        if (retval) {
            retval = mStreamingSocket.isRunning();
        }
        return retval;
    }

    protected void quitMain(boolean error) {
        mWorkQueue.clear();
        mWorkQueue.add(QUIT);

        if (error) {
            OSLog.w(Tag.NETWORK, "Connection severed unexpectedly");
        }
    }

    /**
     * ensures that the first message after a handshake is the metaconnect message, which only comes once per stream in this mode
     */
    protected void onSuccessfulHandshake(ArrayNode requests) {
        requests.add(createMetaConnectMessage());
    }

    protected void onSuccessfulMetaSubscribe() {
        ArrayList<StoredRequest> requests;

        // make sure this operation is atomic with block in addRequest()
        synchronized (mLock) {
            mPrimaryConnectionEstablished = true;

            // copy all of the stored requests batched up since connection
            // started
            requests = new ArrayList<>(mStoredRequests);

            // and clear them
            mStoredRequests.clear();
        }

        // now execute each one
        for (StoredRequest request : requests) {
            addRequest(request.mRequest, request.mFuture);
        }
    }

    /**
     * subscriptions establish a permanent response behavior
     */
    protected void addSubscription(JsonNode request) {
        mSubscriptionCountMonitor.enter();
        try {
            mSubscriptionCount++;
        } finally {
            mSubscriptionCountMonitor.leave();
        }
        // TODO have a response to the subscription
        addRequest(request, SettableFuture.create());
    }

    /**
     * requests are sent directly to server if the connection is established
     */
    protected void addRequest(JsonNode request, SettableFuture<JsonNode> handler) {
        boolean executeRequestNow = false;

        // make sure this operation is atomic with block in
        // onSuccessfulMetaSubscribe()
        synchronized (mLock) {
            if (mPrimaryConnectionEstablished) {
                executeRequestNow = true;
            } else {
                mStoredRequests.add(new StoredRequest(request, handler));
            }
        }
        if (executeRequestNow) {
            try {
                OSLog.d(Tag.DEFAULT, "addRequest", request);
                ArrayNode payload = mObjectMapper.createArrayNode();
                payload.add(request);

                JsonRpc rpc = new JsonRpc();
                // pull in credentials from this connection
                rpc.setCredentials(mConnectionCredentials);
                rpc.setTimeout(60, TimeUnit.SECONDS);

                Result result = rpc.executeWithSimpleTimeout(getCometUrl(), payload);
                try {
                    OSLog.TimingLoggerCompat requestTimer = Tag.TIMING.newTimingLogger("StreamingConnection::addRequest");
                    for (ObjectNode node : result.getObjects()) {
                        requestTimer.addSplit("iteration");

                        onResponseReceived(node);
                    }
                    requestTimer.close();
                } finally {
                    result.close();
                }
            } catch (InterruptedException e) {
                // don't log this
                handler.setException(e);
            } catch (IOException | JsonRpcException e) {
                OSLog.w(e.getMessage(), e);

                handler.setException(e);
            }
        }
    }

    protected void onResponseReceived(JsonNode node) {
        mWorkQueue.add(new WorkItem(node));
    }

    /**
     * used in the work queue to pass work items from the network threads to the main handler thread
     */
    static private class WorkItem {
        public WorkItem(JsonNode node) {
            mNode = node;
        }

        @Nonnull
        final JsonNode mNode;
    }

    /**
     * queues up requests and associated response handlers until the connection completes
     */
    static private class StoredRequest {

        public StoredRequest(JsonNode request, SettableFuture<JsonNode> future) {
            mRequest = request;
            mFuture = future;
        }

        @Nonnull
        final JsonNode mRequest;

        @Nonnull
        final SettableFuture<JsonNode> mFuture;
    }

    @Nonnull
    public URL getCometUrl() {
        return mCometUrl;
    }

    @Nonnull
    protected String getClientId() {
        OSAssert.assertNotNull(mClientId, "client ID should never be null");
        return mClientId;
    }

    protected void setClientId(String clientId) {
        synchronized (mLock) {
            mClientId = clientId;
        }
    }

    public boolean awaitConnection(long duration, TimeUnit units) throws InterruptedException {
        if (!mConnectionComplete.await(duration, units)) {
            return false;
        }

        if (!isRunning()) {
            return false;
        }

        if (didConnectionFail()) {
            return false;
        }

        if (mClientId == null) {
            Reporting.report(new Exception(), "got null client id in connection fail check");
            return false;
        }
        return true;
    }

    public synchronized boolean didConnectionFail() {
        return mConnectionFailed;
    }

    @Nullable
    public synchronized String getConnectionFailureReason() {
        return mConnectionFailureReason;
    }

    public synchronized boolean shouldRetryConnection() {
        return mShouldRetry;
    }

    synchronized void notifyConnectionFailed(String reason, @Nullable Boolean shouldRetry) {
        mConnectionFailed = true;
        mConnectionFailureReason = reason;
        if (shouldRetry != null) {
            mShouldRetry = shouldRetry;
        }
        mConnectionComplete.countDown();
    }

    void notifyConnectionSuccess() {
        mConnectionComplete.countDown();
    }

    /**
     * called to bootstrap the player menu information, might be called before or after the player subscription was completed
     */
    public void initializePlayerMenus(@Nullable Map<PlayerId, PlayerMenuSet> playerMenus) {
        mSubscriptionCountMonitor.enter();
        try {
            if (playerMenus != null) {
                mPlayerMenuPreload.putAll(playerMenus);
            }
            if (mSubscriptionCountIsZero.isSatisfied()) {
                finalizePlayerMenus();
            }
        } finally {
            mSubscriptionCountMonitor.leave();
        }

    }

    /**
     * Called when subscription count drops to zero <br>
     * GuardedBy(mLock) to ensure that the operation is atomic since it is called from at least two separate situations and we only want the
     * first one to occur
     */
    @GuardedBy("mLock")
    protected void finalizePlayerMenus() {
        OSAssert.assertCurrentThreadHoldsMonitor(mSubscriptionCountMonitor);

        ServerStatus serverStatus = mSbContext.getServerStatus();
        Transaction transaction = serverStatus.newTransaction();
        try {
            for (Map.Entry<PlayerId, PlayerMenuSet> entry : mPlayerMenuPreload.entrySet()) {
                PlayerId playerId = entry.getKey();
                PlayerMenus menus = mSbContext.getPlayerMenus(playerId);

                PlayerMenus newMenus = menus.withUpdates(entry.getValue().getMenuMap(), true);
                transaction.add(newMenus);
            }
            transaction.markSuccess();
        } finally {
            transaction.close();
        }
        // clear at end to avoid concurrent modification exception
        mPlayerMenuPreload.clear();
    }

    /**
     * Adds a player subscription for the supplied ID. If the player already has a subscription, this method does nothing.
     *
     * @param playerId the player id for the subscription
     */
    void addPlayerSubscription(PlayerId playerId) {
        OSAssert.assertNotNull(playerId, "player id not null");

        String requestId = getPlayerRequestId(playerId);

        boolean shouldRegister = false;
        PlayerStatusSubscription playerStatusSubscription = new PlayerStatusSubscription(this, playerId);

        // try to put the playerstatus
        Subscription existingSubscription = mSubscriptions.putIfAbsent(requestId, playerStatusSubscription);
        if (existingSubscription == mNullSubscription) {
            // safely replace it, avoiding race conditions
            if (mSubscriptions.replace(requestId, existingSubscription, playerStatusSubscription)) {
                shouldRegister = true;
            }
        } else if (existingSubscription == null) {
            // no existing subscription
            shouldRegister = true;
        }

        if (shouldRegister) {
            JsonNode playerStatusRequest = createPlayerStatusSubscribeMessage(playerId, requestId);
            addSubscription(playerStatusRequest);

            MenuStatusSubscription menuStatusSubscription = new MenuStatusSubscription(this);

            String menuStatusId = getMenuStatusRequestId(playerId);
            // now do menu status subscriptions
            mSubscriptions.put(menuStatusId, menuStatusSubscription);

            JsonNode menuStatusRequest = createMenuStatusSubscribeMessage(playerId, menuStatusId);
            addSubscription(menuStatusRequest);
        }
    }

    void removePlayerSubscription(PlayerId playerId) {
        String requestId = getPlayerRequestId(playerId);

        // because there may be lingering player updates, use the null response handler for these cases to avoid error messages
        mSubscriptions.put(requestId, mNullSubscription);
    }

    protected int getNextRequestId() {
        return mNextSlimRequestId.getAndIncrement();
    }

    @Nonnull
    protected String getMenuStatusRequestId(PlayerId playerId) {
        return "/" + getClientId() + "/slim/menustatus/" + playerId;
    }

    @Nonnull
    protected String getPlayerRequestId(PlayerId playerId) {
        return "/" + getClientId() + "/slim/playerstatus/" + playerId;
    }

    protected boolean handleResponse(final JsonNode response) {
        if (!isRunning()) {
            // we're stopped or stopping
            return false;
        }

        OSLog.d(Tag.DEFAULT, "handleResponse", response);

        boolean success = true;
        final JsonNode successNode = response.get(SUCCESSFUL);
        if (successNode != null) {
            success = successNode.asInt() != 0;
        }

        final JsonNode errorNode = response.get(ERROR);
        if (errorNode != null) {
            String errorString = errorNode.asText();
            if (errorString.equals("forced reconnect")) {
                // force watchdog to restart
                stopAsync();
                return true;
            }
        }

        String channel = response.path(CHANNEL).asText();
        int id = response.path(ID).asInt();

        if (channel.equals("")) {
            // ignore requests with no channel
            return false;
        }

        Throwable failureException = null;
        if (!success) {
            OSLog.w(Tag.DEFAULT, "Unsuccessful operation", response);
            String errorMsg = response.path("error").asText();
            failureException = new UnsuccessfulOperationException(errorMsg, response);
            SettableFuture<JsonNode> requestById = mRequestsById.remove(id);
            if (requestById != null) {
                requestById.setException(failureException);
            }
        }

        boolean handled = false;
        final Subscription subscription = mSubscriptions.get(channel);
        if (subscription != null) {
            handled = true;
            final boolean fSuccess = success;
            final Throwable fFailureException = failureException;
            subscription.getExecutorService().execute(() -> {
                if (fSuccess) {
                    subscription.onSuccess(response);
                } else {
                    subscription.onFailure(fFailureException);
                }
            });
        }

        String requestKey = buildRequestKey(id, channel);
        // even with a subscription, there may be a request
        SettableFuture<JsonNode> requestResponseFuture = mRequests.remove(requestKey);
        mRequestsById.remove(id);
        if (requestResponseFuture != null) {
            handled = true;
            if (success) {
                requestResponseFuture.set(response);
            } else {
                // don't think this will ever happen here
                requestResponseFuture.setException(failureException);
            }
        }

        if (!handled) {
            OSLog.d(Tag.DEFAULT, "No longer care about response", response);
        }
        return handled;
    }

    @Nonnull
    protected JsonNode createHandshakeMessage() {

        ObjectNode ext = mObjectMapper.createObjectNode();
        ext.put("mac", mMacAddress);
        ext.put("uuid", mUUID);
        ext.put("rev", "7.7.1");

        ObjectNode message = createMessage(MESSAGE_META_HANDSHAKE);
        message.put("version", "1.0");
        message.set("ext", ext);

        ArrayNode connTypes = mObjectMapper.createArrayNode();
        connTypes.add(STREAMING);

        message.set("supportedConnectionTypes", connTypes);
        return message;
    }

    protected boolean isMetaConnectMessage(JsonNode node) {
        String channel = node.path(CHANNEL).asText();
        return MESSAGE_META_CONNECT.equals(channel);
    }

    protected boolean isSuccessful(JsonNode node) {
        return node.path(SUCCESSFUL).asBoolean();
    }

    @Nonnull
    protected ObjectNode createMenuStatusSubscribeMessage(PlayerId playerId, String requestId) {

        ArrayNode values = mObjectMapper.createArrayNode();
        values.add("menustatus");

        ArrayNode request = mObjectMapper.createArrayNode();
        request.add(playerId.toString());
        request.add(values);

        ObjectNode retval = createSlimSubscribeMessage(request, getNextRequestId(), requestId);
        return retval;
    }

    @Nonnull
    protected ObjectNode createPlayerStatusSubscribeMessage(PlayerId playerId, String requestId) {

        ArrayNode values = mObjectMapper.createArrayNode();
        values.add("status");
        values.add("-");
        values.add(1);
        values.add("menu:menu");
        values.add("useContextMenu:1");
        values.add("subscribe:" + PLAYER_STATUS_INTERVAL);

        ArrayNode request = mObjectMapper.createArrayNode();
        request.add(playerId.toString());
        request.add(values);

        ObjectNode retval = createSlimSubscribeMessage(request, getNextRequestId(), requestId);
        return retval;
    }

    protected void addServerStatusSubscribeMessage() {

        ArrayNode values = mObjectMapper.createArrayNode();
        values.add("serverstatus");
        values.add(0);
        values.add(50);
        values.add("subscribe:" + SERVER_STATUS_INTERVAL);

        ArrayNode request = mObjectMapper.createArrayNode();
        request.add("");
        request.add(values);

        String response = "/" + getClientId() + "/slim/serverstatus";
        ServerStatusSubscription serverStatus = new ServerStatusSubscription(this);
        mSubscriptions.put(response, serverStatus);
        addSubscription(createSlimSubscribeMessage(request, getNextRequestId(), response));
    }

    protected void addSNRegisterMessage() {

        ArrayNode values = mObjectMapper.createArrayNode();
        values.add("register_sn");
        values.add(0);
        values.add(100);
        values.add("login_password");
        values.add("email:" + mConnectionInfo.getUsername());
        values.add("password:" + mConnectionInfo.getPassword());

        ArrayNode request = mObjectMapper.createArrayNode();
        request.add("");
        request.add(values);

        int requestId = getNextRequestId();

        String response = getResponse("snlogin");
        ObjectNode retval = createSlimRequestMessage(request, requestId, response);

        SettableFuture<JsonNode> future = SettableFuture.create();
        Futures.addCallback(future, new Subscription(this) {
            @Override
            public void onSuccess(@Nullable JsonNode jsonNode) {
                // force watchdog to restart the service
                notifyConnectionFailed("Server requested reconnection", true);
                triggerShutdown();
                stopAsync();
            }
        }, OSExecutors.getUnboundedPool());

        mRequests.put(buildRequestKey(requestId, response), future);
        addRequest(retval, future);
    }

    protected String buildRequestKey(int id, String channel) {
        return "" + id + ":" + channel;
    }

    //
    //	protected JsonNode createSNRegisterMessage() {
    //
    //		ArrayNode values = mObjectMapper.createArrayNode();
    //		values.add("register");
    //		values.add(0);
    //		values.add(100);
    //		values.add("service:SN");
    //
    //		ArrayNode request = mObjectMapper.createArrayNode();
    //		request.add("");
    //		request.add(values);
    //
    //		int requestId = getNextRequestId();
    //		String response = getResponse("snregister");
    //		ObjectNode retval = createSlimRequestMessage(request, requestId, response);
    //		mOneshotSubscriptions.put(requestId, new WeakReference<SBResponseHandler>(NullSubscriptionHandler.getInstance()));
    //		return retval;
    //	}

    @Nonnull
    protected String getResponse(String basis) {
        return "/" + getClientId() + "/slim/" + basis;
    }

    @Nonnull
    protected ObjectNode createSlimSubscribeMessage(ArrayNode request, int id, String response) {

        ObjectNode data = mObjectMapper.createObjectNode();
        data.set(REQUEST, request);
        data.put(RESPONSE, response);

        ObjectNode message = createMessage("/slim/subscribe");
        message.put(ID, id);
        message.set(DATA, data);
        return message;
    }

    @Nonnull
    protected ObjectNode createSlimRequestMessage(ArrayNode request, int id, String response) {
        ObjectNode data = mObjectMapper.createObjectNode();
        data.set("request", request);
        data.put("response", response);

        ObjectNode message = createMessage("/slim/request");
        message.put(ID, id);
        message.set(DATA, data);
        return message;
    }

    @Nonnull
    protected ObjectNode createMetaConnectMessage() {
        ObjectNode message = createMessage(MESSAGE_META_CONNECT);
        message.put(CLIENT_ID, getClientId());
        message.put(CONNECTION_TYPE, STREAMING);
        return message;
    }

    @Nonnull
    protected ObjectNode createMetaSubscribeMessage() {
        ObjectNode message = createMessage("/meta/subscribe");
        message.put(CLIENT_ID, getClientId());
        message.put(SUBSCRIPTION, "/" + getClientId() + "/**");
        return message;
    }

    @Nonnull
    protected ObjectNode createMessage(String channel) {
        ObjectNode message = mObjectMapper.createObjectNode();
        message.put(CHANNEL, channel);
        return message;
    }

    final private Subscription mMetaHandshakeSubscription = new Subscription(this) {

        @Nonnull
        @Override
        public ExecutorService getExecutorService() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        public void onSuccess(@Nullable JsonNode result) {
            OSAssert.assertNotNull(result, "can't be null");

            boolean success = isSuccessful(result);

            String clientId = result.path(CLIENT_ID).asText();

            if (clientId == null || clientId.equals("")) {
                success = false;
            }
            if (success) {
                setClientId(clientId);
                //JsonNode advice = result.get("advice");
                //setTimeout(advice.path("timeout").asLong(), TimeUnit.MILLISECONDS);
                //setInterval(advice.path("interval").asLong(), TimeUnit.MILLISECONDS);

                ArrayNode newRequests = mObjectMapper.createArrayNode();

                // in streaming mode, we need this to send the metaconnect message
                onSuccessfulHandshake(newRequests);

                newRequests.add(createMetaSubscribeMessage());

                sendConnectMessage(newRequests);

                addServerStatusSubscribeMessage();
            }
        }
    };

    final private Subscription mMetaSubscribeSubscription = new Subscription(this) {
        @Override
        public void onSuccess(@Nullable JsonNode result) {
            onSuccessfulMetaSubscribe();
        }
    };

    final private Subscription mSlimSubscribeSubscription = new Subscription(this) {

        @Override
        public void onSuccess(@Nullable JsonNode o) {
            mSubscriptionCountMonitor.enter();
            try {
                mSubscriptionCount--;
                if (mSubscriptionCount == 0) {
                    finalizePlayerMenus();
                }
                OSAssert.assertTrue(mSubscriptionCount >= 0, "expect outstanding subscription count to never go below zero");
            } finally {
                mSubscriptionCountMonitor.leave();
            }
        }

        @Nonnull
        @Override
        public ExecutorService getExecutorService() {
            return MoreExecutors.newDirectExecutorService();
        }
    };

    final private Subscription mConnectResponseSubscription = new Subscription(this) {

        @Override
        public void onFailure(Throwable e) {
            if (e.getMessage().equals("invalid clientId")) {
                // force the watchdog to restart
                stopAsync();
            } else {
                super.onFailure(e);
            }
        }

        @Override
        public void onSuccess(@Nullable JsonNode o) {
            // no implementation
        }

        @Nonnull
        @Override
        public ExecutorService getExecutorService() {
            return MoreExecutors.newDirectExecutorService();
        }
    };

    final private Subscription mNullSubscription = new Subscription(this) {
        @Nonnull
        @Override
        public ExecutorService getExecutorService() {
            return MoreExecutors.newDirectExecutorService();
        }

        @Override
        public void onSuccess(@Nullable JsonNode node) {
            // no implementation
        }
    };

    public static abstract class Subscription implements FutureCallback<JsonNode> {
        @Nonnull
        final protected StreamingConnection mConnection;

        protected Subscription(StreamingConnection connection) {
            mConnection = connection;
        }

        @Nonnull
        public ExecutorService getExecutorService() {
            return OSExecutors.getUnboundedPool();
        }

        @Override
        public void onFailure(Throwable e) {
            OSLog.w(e.getMessage(), e);
        }
    }

    static class UnsuccessfulOperationException extends Exception {
        @Nonnull
        final private JsonNode mNode;

        public UnsuccessfulOperationException(String message, JsonNode node) {
            super(message);

            mNode = node;
        }
    }
}