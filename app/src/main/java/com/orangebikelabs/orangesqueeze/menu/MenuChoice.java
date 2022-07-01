/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.menu;

import androidx.annotation.Keep;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.orangebikelabs.orangesqueeze.common.PlayerId;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
@JsonIgnoreProperties(ignoreUnknown = true)
public class MenuChoice implements Serializable {

    @Nonnull
    private List<String> mCommands = Collections.emptyList();

    @Nullable
    private PlayerId mPlayerId;

    private boolean mShouldIncludePlayerId = false;

    public MenuChoice() {
    }

    @JsonProperty(value = "cmd")
    @Nonnull
    public List<String> getCommands() {
        return mCommands;
    }

    @JsonProperty(value = "cmd")
    public void setCommands(@Nullable List<String> commands) {
        if (commands == null) {
            mCommands = Collections.emptyList();
        } else {
            mCommands = new ArrayList<>(commands);

            // remove null elements, if any
            mCommands.removeAll(Collections.singleton((String) null));
        }
    }

    @Nullable
    public PlayerId getPlayer() {
        return mPlayerId;
    }

    public void setPlayerId(@Nullable String playerId) {
        mPlayerId = PlayerId.newInstance(playerId);
        mShouldIncludePlayerId = mPlayerId != null || Objects.equal(playerId, "0");
    }

    public boolean shouldIncludePlayer() {
        return mShouldIncludePlayerId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mCommands, mPlayerId, mShouldIncludePlayerId);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MenuChoice that = (MenuChoice) o;

        if (mShouldIncludePlayerId != that.mShouldIncludePlayerId) {
            return false;
        }
        if (!mCommands.equals(that.mCommands)) {
            return false;
        }
        if (!Objects.equal(mPlayerId, that.mPlayerId)) {
            return false;
        }

        return true;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("cmd", mCommands).
                add("playerId", mPlayerId).
                add("shouldIncludePlayerId", mShouldIncludePlayerId).
                toString();
    }
}
