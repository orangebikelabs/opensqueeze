/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.content.Context;

import androidx.annotation.Keep;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.EncryptionTools;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBContextWrapper;
import com.orangebikelabs.orangesqueeze.common.SBException;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;
import com.orangebikelabs.orangesqueeze.common.VersionIdentifier;
import com.orangebikelabs.orangesqueeze.common.WakeOnLanSettings;
import com.orangebikelabs.orangesqueeze.common.event.PendingConnectionState;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.database.Server;
import com.orangebikelabs.orangesqueeze.net.BasicHttpServerCredentials;
import com.orangebikelabs.orangesqueeze.net.DeviceConnectivity;
import com.orangebikelabs.orangesqueeze.net.JsonRpcException;
import com.orangebikelabs.orangesqueeze.net.NetworkTools;
import com.orangebikelabs.orangesqueeze.net.SBCredentials;
import com.orangebikelabs.orangesqueeze.net.StreamingConnection;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
@ThreadSafe
public class PendingConnection {

    static class AuthenticationException extends SBException {
        // no overrides
    }

    static class ConnectionException extends SBException {
        public ConnectionException(String detailMessage, Throwable t) {
            super(detailMessage, t);
        }

        public ConnectionException(String detailMessage) {
            super(detailMessage);
        }
    }

    public enum PendingState {
        INIT, STARTED, FAILED_ERROR, FAILED_NEED_CREDENTIALS, ABORTED, SUCCESS
    }

    final protected ContextImpl mMasterContext;
    final protected long mServerId;
    final protected String mServerName;

    final protected Context mContext;
    final protected SBContextWrapper mSbContext;
    final protected ServerStatus mTemporaryServerStatus;

    @GuardedBy("this")
    @Nullable
    private StreamingConnection mSubscriptionConnection;

    @GuardedBy("this")
    @Nullable
    private ConnectionInfo mConnectedInfo;

    @Nonnull
    final private AtomicReference<PendingState> mState = Atomics.newReference(PendingState.INIT);

    @GuardedBy("this")
    @Nullable
    private String mFailureReason;

    @GuardedBy("this")
    @Nullable
    private PlayerId mPlayerId;

    @GuardedBy("this")
    @Nullable
    private PlayerId mDesiredPlayerId;

    @GuardedBy("this")
    @Nullable
    private Thread mInterruptThread;

    @GuardedBy("this")
    @Nonnull
    final protected List<String> mRootMenuNodes = new ArrayList<>();

    public PendingConnection(ContextImpl masterContext, long serverId, String serverName) {
        OSAssert.assertNotNull(masterContext, "non-null context");
        mContext = masterContext.getApplicationContext();
        mTemporaryServerStatus = new ServerStatus();
        mMasterContext = masterContext;
        mServerId = serverId;
        mServerName = serverName;
        mSbContext = SBContextProvider.mutableWrapper(new IsolatedContext());
    }

    public ListenableFuture<PendingState> submit(ListeningExecutorService executor) {
        mState.set(PendingState.STARTED);
        return executor.submit(mCallable);
    }

    @Nonnull
    public String getServerName() {
        return mServerName;
    }

    public PendingState getState() {
        return mState.get();
    }

    @Nonnull
    synchronized public Optional<String> getFailureReason() {
        return Optional.ofNullable(mFailureReason);
    }

    synchronized protected void setFailureReason(@Nullable String failureReason) {
        mFailureReason = failureReason;
    }

    public long getServerId() {
        return mServerId;
    }

    @Nullable
    public synchronized PlayerId getActualPlayerId() {
        return mPlayerId;
    }

    @Nullable
    public synchronized PlayerId getDesiredPlayerId() {
        return mDesiredPlayerId;
    }

    public synchronized boolean isConnected() {
        return mConnectedInfo != null;
    }

    @Nonnull
    public synchronized ConnectionInfo getConnectedInfo() {
        if (mConnectedInfo == null) {
            throw new IllegalStateException();
        }
        return mConnectedInfo;
    }

    private synchronized void setConnectedInfo(ConnectionInfo ci) {
        mConnectedInfo = ci;
    }

    public synchronized void setSubscriptionConnection(StreamingConnection service) {
        mSubscriptionConnection = service;
    }

    @Nullable
    public synchronized StreamingConnection getSubscriptionConnection() {
        return mSubscriptionConnection;
    }

    @Nonnull
    public synchronized ServerStatus getServerStatus() {
        return mTemporaryServerStatus;
    }

    @Nonnull
    public synchronized List<String> getRootMenuNodes() {
        return mRootMenuNodes;
    }

    public synchronized void setRootMenuNodes(@Nullable List<String> nodes) {
        mRootMenuNodes.clear();
        if (nodes != null) {
            mRootMenuNodes.addAll(nodes);
        }
    }

    @Nonnull
    public ListenableFuture<Boolean> setConnectionCredentials(String username, String password) {
        OSLog.v("setConnectionCredentials(username,password))");
        return OSExecutors.getUnboundedPool().submit(() -> {
            boolean retval = setServerCredentials(mServerId, username, password);
            OSLog.v("setConnectionCredentials result=" + retval);
            return retval;
        });
    }

    protected boolean setServerCredentials(long serverId, String username, String password) throws SBRequestException {
        Server server = DatabaseAccess.getInstance(mContext)
                .getServerQueries()
                .lookupById(serverId).executeAsOneOrNull();
        if (server == null) {
            OSLog.v("internalSetServerCredentials early exit");
            return false;
        }

        String hostname = server.getServerhost();
        String sid = null;
        boolean retval = false;
        try {
            switch (server.getServertype()) {
                case DISCOVERED:
                case PINNED: {
                    BasicHttpServerCredentials credentials = new BasicHttpServerCredentials(mContext, hostname, server.getServerport());
                    credentials.set(username, password.toCharArray());

                    retval = credentials.checkCredentials(Constants.CONNECTION_TIMEOUT, Constants.TIME_UNITS);
                    break;
                }
            }
        } catch (InterruptedException e) {
            // ignore this
        }
        if (retval) {
            try {
                String key = EncryptionTools.generateKey();
                String encryptedPassword = EncryptionTools.encrypt(key, password.getBytes(Charsets.UTF_8.name()));

                DatabaseAccess.getInstance(mContext)
                        .getServerQueries()
                        .updateCredentials(key, username, encryptedPassword, sid, serverId);
            } catch (GeneralSecurityException e) {
                OSLog.e("encryption error", e);
            } catch (UnsupportedEncodingException e) {
                OSLog.e("unsupported encoding exception", e);
            }
        }
        OSLog.v("internalSetServerCredentials retval=" + retval);
        return retval;
    }

    /**
     * called to convert the temporary context operations into permanent ones associated with the mastercontext
     */
    public void activate() {
        // preserve any credential objects we've established
        SBCredentials creds = mSbContext.getConnectionCredentials();

        String serverVersion = mSbContext.getServerStatus().getVersion().toString();
        OSLog.d("server version: " + serverVersion);

        // reset the base context
        mSbContext.setBase(mMasterContext);

        // reapply the credentials to the master context directly
        mMasterContext.setConnectionCredentials(creds);
    }

    private boolean isAborted() {
        return mState.get() == PendingState.ABORTED;
    }

    public void abort() {
        mState.set(PendingState.ABORTED);

        // if we're waiting on a lock, interrupt it
        interruptThread();

        StreamingConnection service = getSubscriptionConnection();
        if (service != null) {
            service.stopAsync();
        }
    }

    public boolean isConnecting() {
        return mState.get() == PendingState.STARTED;
    }

    final private Callable<PendingState> mCallable = new Callable<>() {
        @Nonnull
        @Override
        public PendingState call() {
            initializeThread();

            PendingState newState = PendingState.ABORTED;
            try {
                boolean ready = performConnect();
                if (ready) {
                    OSLog.i("Pending connection ready " + getConnectedInfo());
                    newState = PendingState.SUCCESS;
                } else {
                    newState = PendingState.ABORTED;
                }
            } catch (AuthenticationException e) {
                // we've detected invalid credentials
                newState = PendingState.FAILED_NEED_CREDENTIALS;
            } catch (GeneralSecurityException e) {
                OSLog.w("Corrupt stored credentials, try resetting them");
                newState = PendingState.FAILED_NEED_CREDENTIALS;
            } catch (InterruptedException e) {
                newState = PendingState.ABORTED;
            } catch (Exception e) {
                OSLog.e("connection exception", e);
                // some other I/O error on connection
                setFailureReason(getFailureMessage(e));
                newState = PendingState.FAILED_ERROR;
            } finally {
                mState.set(newState);

                BusProvider.getInstance().post(new PendingConnectionState(false, PendingConnection.this));
            }
            return newState;
        }
    };

    @Nullable
    protected String getFailureMessage(Exception e) {
        Throwable cause = e;
        if (e instanceof JsonRpcException) {
            if (e.getCause() != null) {
                cause = e.getCause();
            }
        } else if (e instanceof SBRequestException) {
            if (e.getCause() != null) {
                cause = e.getCause();
            }
        }
        String message = cause.getMessage();
        if (cause instanceof SocketTimeoutException) {
            message = "Timeout waiting for server " + getConnectedInfo().getServerHost() + ":" + getConnectedInfo().getServerPort() + " to respond";
        }
        return message;
    }

    synchronized protected void initializeThread() {
        mInterruptThread = Thread.currentThread();
    }

    synchronized protected void interruptThread() {
        if (mInterruptThread != null) {
            mInterruptThread.interrupt();
        }
    }

    /**
     * @return true if connect successful
     */
    protected boolean performConnect() throws Exception {
        OSLog.TimingLoggerCompat timingLogger = Tag.TIMING.newTimingLogger("PendingConnection::performConnect");

        PlayerId desiredPlayerId;
        if (!DeviceConnectivity.Companion.getInstance().awaitNetwork(Constants.CONNECTION_TIMEOUT, Constants.TIME_UNITS)) {
            throw new ConnectionException(mContext.getString(R.string.exception_no_network_available));
        }

        timingLogger.addSplit("network available");

        Server server = DatabaseAccess.getInstance(mContext).getServerQueries().lookupById(mServerId).executeAsOneOrNull();
        if (server == null) {
            throw new ConnectionException("internal error: no server data");
        }

        timingLogger.addSplit("server list cursor ready");

        String serverHost = server.getServerhost();
        int serverPort = server.getServerport();

        desiredPlayerId = server.getServerlastplayer();
        String serverName = server.getServername();

        String username = server.getServerusername();
        String encryptedPassword = server.getServerpassword();
        String key = server.getServerkey();
        WakeOnLanSettings wolSettings = server.getServerwakeonlan();

        timingLogger.addSplit("wake on lan settings retrieved");

        List<String> rootMenuNodes = server.getServermenunodes();
        final Map<PlayerId, PlayerMenuHelper.PlayerMenuSet> playerMenuData = server.getServerplayermenus();
        timingLogger.addSplit("menu data retrieved");

        String password = null;

        if (encryptedPassword != null && key != null) {
            byte[] pw = EncryptionTools.decrypt(key, encryptedPassword);
            password = new String(pw, Charsets.UTF_8.name());
        }

        setConnectedInfo(ConnectionInfo.newInstance(true, mServerId, serverHost, serverPort, serverName, username, password, wolSettings));

        timingLogger.addSplit("connection info initialized");

        // before we try to contact the server for version information, make
        // sure WOL packet is sent
        NetworkTools.sendWakeOnLan(mContext, getConnectedInfo());

        timingLogger.addSplit("wake on lan sent");

        VersionIdentifier serverVersion = checkServerVersion();

        timingLogger.addSplit("server version identified: " + serverVersion);

        StreamingConnection newSubscriptionConnection = null;

        int keepTrying = 3;
        while (keepTrying-- > 0) {
            newSubscriptionConnection = new StreamingConnection(mSbContext);

            timingLogger.addSplit("new connection created");

            // this triggers the server start
            newSubscriptionConnection.startAsync();

            timingLogger.addSplit("new connection started");

            setRootMenuNodes(rootMenuNodes);

            final StreamingConnection fConnection = newSubscriptionConnection;
            OSExecutors.getUnboundedPool().execute(() -> {
                if (fConnection.didConnectionFail()) return;

                fConnection.initializePlayerMenus(playerMenuData);
            });
            timingLogger.addSplit("player menus loaded");

            // wait for player list to be available
            if (newSubscriptionConnection.awaitConnection(Constants.CONNECTION_TIMEOUT, Constants.TIME_UNITS)) {
                // connection SUCCESS
                break;
            }

            // connection failure
            String reason = newSubscriptionConnection.getConnectionFailureReason();
            if (reason == null) {
                reason = mContext.getString(R.string.exception_connection_timeout);
            }
            OSLog.i(reason);
            if (!newSubscriptionConnection.shouldRetryConnection()) {
                throw new ConnectionException(reason);
            }
        }

        OSAssert.assertNotNull(newSubscriptionConnection, "can't be null here");

        timingLogger.addSplit("connection available");

        setSubscriptionConnection(newSubscriptionConnection);

        setActivePlayerId(desiredPlayerId);

        timingLogger.addSplit("player id set");

        if (getConnectedInfo().getWakeOnLanSettings().getAutodetectMacAddress()) {
            updateConnectionMacAddress();

            timingLogger.addSplit("mac address updated");
        }

        if (isAborted()) {
            // stop the subscription, we aborted
            newSubscriptionConnection.stopAsync();
        }
        timingLogger.close();
        return !isAborted();
    }

    private void updateConnectionMacAddress() {
        // avoid synchronization in this method because it may block in hostname
        // lookup
        ConnectionInfo ci = getConnectedInfo();

        String newMacAddress = null;
        try {
            // get the server mac address from the ARP cache, if we have
            // it and update the stored mac address
            String ip = InetAddress.getByName(ci.getServerHost()).getHostAddress();
            newMacAddress = NetworkTools.getMacFromArpCache(ip);
        } catch (UnknownHostException e) {
            OSLog.i(Tag.DEFAULT, "saving mac address: " + e.getMessage(), e);
        }
        if (newMacAddress != null) {
            ci.getWakeOnLanSettings().setMacAddress(newMacAddress);
        }
    }

    private synchronized void setActivePlayerId(@Nullable PlayerId desiredPlayerId) {
        mDesiredPlayerId = desiredPlayerId;
        if (mDesiredPlayerId != null && mTemporaryServerStatus.isConnectedPlayerId(mDesiredPlayerId)) {
            mPlayerId = mDesiredPlayerId;
        }

        if (mPlayerId == null) {
            PlayerStatus firstPlayer = null;
            // look for a powered player
            for (PlayerStatus ps : mTemporaryServerStatus.getAvailablePlayers()) {
                if (firstPlayer == null) {
                    firstPlayer = ps;
                }
                if (ps.isPowered()) {
                    mPlayerId = ps.getId();
                    break;
                }
            }

            if (mPlayerId == null && firstPlayer != null) {
                mPlayerId = firstPlayer.getId();
            }
        }
    }

    /**
     * verifies authentication credentials and also returns the server version
     */
    @Nonnull
    private VersionIdentifier checkServerVersion() throws SBException, InterruptedException {

        String version = null;
        ConnectionInfo ci = getConnectedInfo();

        String username = ci.getUsername();
        String password = ci.getPassword();

        // try five times with increasing timeout to make sure woken servers are available
        final int maxRetryCount = 5;
        int currentRetryCount = maxRetryCount;
        Throwable lastException = null;
        while (version == null && currentRetryCount > 0) {
            checkAborted();
            if (username != null && password != null) {
                BasicHttpServerCredentials credentials = new BasicHttpServerCredentials(mContext, ci.getServerHost(), ci.getServerPort());
                credentials.set(username, password.toCharArray());

                // we will find out with the 401 code below if the
                // credentials are valid
                mSbContext.setConnectionCredentials(credentials);
            }

            try {
                JsonRpcRequest request = (JsonRpcRequest) mSbContext.newRequest(SBRequest.Type.JSONRPC, "version", "?");
                // use reduced timeouts
                request.setTimeout((maxRetryCount - currentRetryCount) * 2L + 4, TimeUnit.SECONDS);

                FutureResult futureResult = request.submit(MoreExecutors.newDirectExecutorService());
                SBResult result = futureResult.checkedGet();
                JsonNode node = result.getJsonResult().get("_version");
                if (node != null && node.isTextual()) {
                    version = node.asText();
                }
            } catch (JsonRpcException e) {
                if (e.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new AuthenticationException();
                }
                OSLog.w(e.getMessage(), e);
                lastException = e;
            } catch (SBRequestException e) {
                checkAborted();
                OSLog.w(e.getMessage(), e);
                lastException = e;
            }
            currentRetryCount--;
        }
        checkAborted();
        if (version == null) {
            if (lastException != null) {
                Throwables.propagateIfPossible(lastException, SBException.class);
            }
            if (lastException == null) {
                throw new ConnectionException(mContext.getString(R.string.exception_connection_error));
            } else {
                throw new ConnectionException(mContext.getString(R.string.exception_connection_error), lastException);
            }
        }
        return new VersionIdentifier(version);
    }

    private void checkAborted() throws InterruptedException {
        if (isAborted()) {
            throw new InterruptedException("aborted");
        }
    }

    @Keep
    private class IsolatedContext extends AbsContext {
        @GuardedBy("this")
        @Nullable
        private SBCredentials mCredentials;

        protected IsolatedContext() {
        }

        @Override
        @Nonnull
        public List<String> getRootBrowseNodes() {
            return PendingConnection.this.getRootMenuNodes();
        }

        @Override
        public void setRootBrowseNodes(List<String> nodes) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        @Nonnull
        public ConnectionInfo getConnectionInfo() {
            return getConnectedInfo();
        }

        @Override
        public boolean awaitConnection(String where) {
            return true;
        }

        @Override
        @Nonnull
        public ServerStatus getServerStatus() {
            return PendingConnection.this.getServerStatus();
        }

        @Override
        @Nonnull
        public SBRequest newRequest(List<?> commands) {
            JsonRpcRequest retval = new JsonRpcRequest(mContext, mSbContext, commands);
            return retval;
        }

        @Override
        @Nonnull
        public SBRequest newRequest(SBRequest.Type requestType, List<?> commands) {
            return newRequest(commands);
        }

        @Override
        @Nullable
        public PlayerId getPlayerId() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean setPlayerById(@Nullable PlayerId playerId) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void setAutoSelectSqueezePlayer(boolean autoSelect) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void startPendingConnection(long serverId, String displayName) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean abortPendingConnection() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean finalizePendingConnection() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void disconnect() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean isConnecting() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        synchronized public void setConnectionCredentials(@Nullable SBCredentials credentials) {
            mCredentials = credentials;
        }

        @Override
        @Nullable
        synchronized public SBCredentials getConnectionCredentials() {
            return mCredentials;
        }

        @Override
        @Nonnull
        public FutureResult sendPlayerCommand(@Nullable PlayerId playerId, List<?> commands) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        @Nonnull
        public FutureResult setPlayerVolume(PlayerId playerId, int volume) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        @Nonnull
        public FutureResult incrementPlayerVolume(PlayerId playerId, int volumeDiff) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        @Nonnull
        public FutureResult unsynchronize(PlayerId playerId) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        @Nonnull
        public FutureResult synchronize(PlayerId playerId, PlayerId targetPlayerId) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        @Nonnull
        public FutureResult renamePlayer(PlayerId playerId, String newName) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void onStart(Context context) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void onStop(Context context) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void temporaryOnStart(Context context, long time, TimeUnit units) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void onServiceCreate(Context context) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void onServiceDestroy(Context context) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void startAutoConnect() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        @Nonnull
        public Context getApplicationContext() {
            return mMasterContext.getApplicationContext();
        }
    }
}
