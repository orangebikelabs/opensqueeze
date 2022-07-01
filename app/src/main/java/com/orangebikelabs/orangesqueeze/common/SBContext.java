/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.content.Context;
import androidx.annotation.Keep;

import com.orangebikelabs.orangesqueeze.net.SBCredentials;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep // because used with reflection
public interface SBContext {

    @Nonnull
    ConnectionInfo getConnectionInfo();

    @Nonnull
    ServerStatus getServerStatus();

    @Nullable
    PlayerId getPlayerId();

    boolean isConnected();

    boolean isConnecting();

    @Nonnull
    Context getApplicationContext();

    @Nullable
    SBCredentials getConnectionCredentials();

    @Nonnull
    SBRequest newRequest(List<?> commands);

    @Nonnull
    SBRequest newRequest(String... commands);

    @Nonnull
    SBRequest newRequest(SBRequest.Type requestType, String... commands);

    @Nonnull
    SBRequest newRequest(SBRequest.Type requestType, List<?> commands);

    @Nonnull
    PlayerStatus getCheckedPlayerStatus() throws PlayerNotFoundException;

    @Nullable
    PlayerStatus getPlayerStatus();

    long getServerId();

    void startPendingConnection(long serverId, String displayServerName);

    boolean abortPendingConnection();

    boolean finalizePendingConnection();

    void disconnect();

    @Nonnull
    FutureResult sendPlayerCommand(List<?> commands);

    @Nonnull
    FutureResult sendPlayerCommand(String... commands);

    @Nonnull
    FutureResult sendPlayerCommand(@Nullable PlayerId playerId, List<?> commands);

    @Nonnull
    FutureResult sendPlayerCommand(@Nullable PlayerId playerId, String... commands);

    @Nonnull
    FutureResult setPlayerVolume(PlayerId playerId, int volume);

    @Nonnull
    FutureResult incrementPlayerVolume(PlayerId playerId, int volumeDiff);

    boolean setPlayerById(@Nullable PlayerId playerId);

    /**
     * given a player id, unsync it from its group
     *
     * @param playerId the player id to unsync
     */

    @Nonnull
    FutureResult unsynchronize(PlayerId playerId);

    /**
     * given a player and a target player, sync player to the target player
     */

    @Nonnull
    FutureResult synchronize(PlayerId playerId, PlayerId targetPlayerId);

    @Nonnull
    FutureResult renamePlayer(PlayerId playerId, String newName);

    void setAutoSelectSqueezePlayer(boolean autoSelect);

    @Nonnull
    List<String> getRootBrowseNodes();

    void setRootBrowseNodes(List<String> nodes);

    @Nonnull
    PlayerMenus getPlayerMenus(PlayerId playerId);

    void setConnectionCredentials(@Nullable SBCredentials credentials);

    boolean awaitConnection(String where) throws InterruptedException;

    void onStart(Context context);

    void onStop(Context context);

    void temporaryOnStart(Context context, long time, TimeUnit units);

    void onServiceCreate(Context context);

    void onServiceDestroy(Context context);

    void startAutoConnect();
}
