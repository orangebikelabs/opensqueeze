/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.menu;

import androidx.annotation.Keep;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
@Immutable
@JsonIgnoreProperties(ignoreUnknown = true)
public class MenuAction implements Serializable {
    @Nullable
    final private String mName;

    @Nonnull
    final private LinkedHashMap<String, String> mParams;

    @Nullable
    final private String mItemsParams;

    @Nonnull
    final private List<String> mCommands;

    @Nullable
    final private String mPlayer;

    @Nonnull
    final private LinkedHashMap<String, String> mWindow;

    @Nullable
    final private String mNextWindow;

    @Nonnull
    final private List<MenuChoice> mChoices;

    @JsonCreator
    public MenuAction(JsonNode node) throws IOException {
        mName = null;
        mItemsParams = JsonHelper.getString(node, "itemsParams", null);
        JsonNode commandNode = node.get("cmd");
        if (commandNode != null) {
            if (commandNode.isArray()) {
                mCommands = new ArrayList<>();
                for (int i = 0; i < commandNode.size(); i++) {
                    mCommands.add(commandNode.get(i).asText());
                }
            } else {
                mCommands = Collections.singletonList(commandNode.asText());
            }
        } else {
            mCommands = Collections.emptyList();
        }

        mParams = JsonHelper.getMap(node, "params");
        mWindow = JsonHelper.getMap(node, "window");

        mPlayer = JsonHelper.getString(node, "player", null);
        mNextWindow = JsonHelper.getString(node, "nextWindow", null);

        JsonNode choicesNode = node.get("choices");
        if (choicesNode != null && choicesNode.size() > 0) {
            ObjectReader reader = JsonHelper.getJsonObjectReader().forType(new TypeReference<List<MenuChoice>>() {
            });
            mChoices = reader.readValue(choicesNode);
        } else {
            mChoices = Collections.emptyList();
        }
    }

    private MenuAction(@Nullable String name, MenuAction base) {
        mName = name;
        mItemsParams = base.mItemsParams;
        mCommands = base.mCommands;
        mParams = base.mParams;
        mWindow = base.mWindow;
        mPlayer = base.mPlayer;
        mNextWindow = base.mNextWindow;
        mChoices = base.mChoices;
    }

    @Nullable
    public String getName() {
        return mName;
    }

    @Nonnull
    public MenuAction withName(String name) {
        return new MenuAction(name, this);
    }

    @Nonnull
    public Map<String, String> getParams() {
        return mParams;
    }

    @Nullable
    public String getItemsParams() {
        return mItemsParams;
    }

    @Nonnull
    public List<MenuChoice> getChoices() {
        return mChoices;
    }

    @JsonProperty(value = "cmd")
    @Nonnull
    public List<String> getCommands() {
        return mCommands;
    }

    @Nullable
    public String getPlayer() {
        return mPlayer;
    }

    @Nonnull
    public Map<String, String> getWindow() {
        return mWindow;
    }

    @Nullable
    public String getNextWindow() {
        return mNextWindow;
    }

    @JsonIgnore
    public boolean isPlayAction() {
        if (matchPlaylistCommand("load")) {
            return true;
        }
        if (containsCommands("playlist", "play")) {
            return true;
        }
        if (containsCommands("jiveplaytrackalbum")) {
            return true;
        }
        if (containsCommands("playlist", "jump")) {
            return true;
        }
        if (matchCommands("custombrowse", "play")) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean isPlayNextAction() {
        if (matchPlaylistCommand("insert")) {
            return true;
        }
        if (containsCommands("playlist", "insert")) {
            return true;
        }
        if (containsCommands("playlist", "move")) {
            return true;
        }
        if (matchCommands("custombrowse", "insert")) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean isAddAction() {
        if (matchPlaylistCommand("add")) {
            return true;
        }
        if (containsCommands("playlist", "add")) {
            return true;
        }
        if (matchCommands("custombrowse", "add")) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean isRandomPlayAction() {
        if (containsCommands("randomplay")) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean isAddFavoriteAction() {
        if (matchCommands("jivefavorites", "add")) {
            return true;
        }

        return false;
    }

    @JsonIgnore
    public boolean isRemoveFavoriteAction() {
        if (matchCommands("jivefavorites", "delete")) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean isRemoveFromPlaylistAction() {
        if (containsCommands("playlist", "delete")) {
            return true;
        }
        return false;
    }

    @JsonIgnore
    protected boolean matchCommands(String... commands) {
        return mCommands.equals(Arrays.asList(commands));
    }

    protected boolean containsCommands(String... commands) {
        return Collections.indexOfSubList(mCommands, Arrays.asList(commands)) != -1;
    }

    @JsonIgnore
    protected boolean matchPlaylistCommand(String cmdParam) {
        boolean retval = false;
        if (matchCommands("playlistcontrol")) {
            String p = mParams.get("cmd");
            if (p != null && p.equals(cmdParam)) {
                retval = true;
            }
        }
        return retval;
    }

    @JsonIgnore
    public boolean isBrowseAction(MenuElement element) {
        return MenuHelpers.getNextWindow(element, this) == null;
    }

    @JsonIgnore
    public boolean isContextMenu() {
        return getParams().containsKey("isContextMenu");
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mName, mItemsParams, mCommands, mParams, mWindow, mPlayer, mNextWindow, mChoices);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MenuAction that = (MenuAction) o;

        if (!mChoices.equals(that.mChoices)) {
            return false;
        }
        if (!mCommands.equals(that.mCommands)) {
            return false;
        }
        if (mItemsParams != null ? !mItemsParams.equals(that.mItemsParams) : that.mItemsParams != null) {
            {
                return false;
            }
        }
        if (mName != null ? !mName.equals(that.mName) : that.mName != null) {
            return false;
        }
        if (mNextWindow != null ? !mNextWindow.equals(that.mNextWindow) : that.mNextWindow != null) {
            return false;
        }
        if (!mParams.equals(that.mParams)) {
            return false;
        }
        if (mPlayer != null ? !mPlayer.equals(that.mPlayer) : that.mPlayer != null) {
            return false;
        }
        if (!mWindow.equals(that.mWindow)) {
            return false;
        }

        return true;
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("name", mName).
                add("params", mParams).
                add("itemsParams", mItemsParams).
                add("commands", mCommands).
                add("player", mPlayer).
                add("window", mWindow).
                add("nextWindow", mNextWindow).
                add("choices", mChoices).
                toString();
    }

}
