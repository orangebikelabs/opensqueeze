/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.content.Context;

import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.SBRequest.Type;
import com.orangebikelabs.orangesqueeze.common.ServerStatus;
import com.orangebikelabs.orangesqueeze.net.SBCredentials;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class NoopContextImpl extends AbsContext {

    public NoopContextImpl() {
    }

    @Override
    @Nonnull
    public ConnectionInfo getConnectionInfo() {
        return ConnectionInfo.newDisconnected();
    }

    @Override
    @Nonnull
    public ServerStatus getServerStatus() {
        return new ServerStatus();
    }

    @Override
    @Nullable
    public PlayerId getPlayerId() {
        return null;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    @Nullable
    public SBCredentials getConnectionCredentials() {
        return null;
    }

    @Override
    @Nonnull
    public SBRequest newRequest(List<?> commands) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    @Nonnull
    public SBRequest newRequest(Type requestType, List<?> commands) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void startPendingConnection(long serverId, String displayServerName) {
        // intentionally blank
    }

    @Override
    public boolean abortPendingConnection() {
        return false;
    }

    @Override
    public boolean finalizePendingConnection() {
        return false;
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
    public void disconnect() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void setConnectionCredentials(@Nullable SBCredentials credentials) {
        throw new UnsupportedOperationException("not implemented");
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
    @Nonnull
    public List<String> getRootBrowseNodes() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void setRootBrowseNodes(List<String> nodes) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean awaitConnection(String where) throws InterruptedException {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    @Nonnull
    public Context getApplicationContext() {
        throw new UnsupportedOperationException("not implemented");
    }
}
