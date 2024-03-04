/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.annotation.Keep;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;

import java.io.Serializable;
import java.text.Collator;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author tsandee
 */
@Keep
@NotThreadSafe
@JsonIgnoreProperties(ignoreUnknown = true)
public class OtherPlayerInfo implements Serializable, Comparable<OtherPlayerInfo> {


    private PlayerId mPlayerId;
    private String mServerUrl;
    private String mModel;
    private String mName = "";
    private String mServer = "";
    private String mSnId;

    private final Map<String, Object> mUnknownProperties = new HashMap<>();

    @JsonAnySetter
    public void handleUnknown(String key, Object value) {
        mUnknownProperties.put(key, value);
    }

    @JsonIgnore
    public PlayerId getId() {
        return mPlayerId;
    }

    @JsonIgnore
    public void setId(PlayerId playerId) {
        mPlayerId = playerId;
    }

    @JsonGetter("id")
    @Nullable
    public String getSNId() {
        return mSnId;
    }

    @JsonSetter("id")
    public void setSNId(String id) {
        mSnId = id;
    }

    @JsonGetter("playerid")
    @Nullable
    public String getIdAsString() {
        return mPlayerId == null ? null : mPlayerId.toString();
    }

    @JsonSetter("playerid")
    public void setIdAsString(String id) {
        mPlayerId = new PlayerId(id);
    }

    @JsonGetter("serverurl")
    @Nullable
    public String getServerUrl() {
        return mServerUrl;
    }

    @JsonGetter("serverurl")
    public void setServerUrl(String serverUrl) {
        mServerUrl = serverUrl;
    }

    @Nullable
    public String getModel() {
        return mModel;
    }

    public void setModel(String model) {
        mModel = model;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    @JsonGetter("server")
    public String getServerName() {
        return mServer;
    }

    @JsonSetter("server")
    public void setServerName(String serverName) {
        this.mServer = serverName;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("id", mPlayerId).
                add("name", mName).
                add("model", mModel).
                add("serverName", mServer).
                add("serverUrl", mServerUrl).
                add("squeezeNetworkId", mSnId).
                add("unknown", mUnknownProperties).
                toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mPlayerId, mServerUrl, mModel, mName, mServer, mSnId);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof OtherPlayerInfo another)) {
            return false;
        }

        // @formatter:off
        return
                Objects.equal(mPlayerId, another.mPlayerId) &&
                        Objects.equal(mServerUrl, another.mServerUrl) &&
                        Objects.equal(mModel, another.mModel) &&
                        Objects.equal(mName, another.mName) &&
                        Objects.equal(mServer, another.mServer) &&
                        Objects.equal(mSnId, another.mSnId) &&
                        Objects.equal(mUnknownProperties, another.mUnknownProperties);
        // @formatter:on
    }

    @Override
    public int compareTo(OtherPlayerInfo another) {
        Collator collator = StringTools.getNullSafeCollator();

        // @formatter:off
        return ComparisonChain.start().
                compare(mServer, another.mServer, collator).
                compare(mName, another.mName, collator).
                compare(mPlayerId, another.mPlayerId).
                compare(mModel, another.mModel).
                compare(mServerUrl, another.mServerUrl).

                result();
        // @formatter:on
    }
}
