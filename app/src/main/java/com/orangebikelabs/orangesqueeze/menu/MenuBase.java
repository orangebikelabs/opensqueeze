/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import androidx.annotation.Keep;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.MoreObjects;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.SBResult;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
@JsonIgnoreProperties(ignoreUnknown = true)
public class MenuBase implements Serializable {
    static public MenuBase get(SBResult result) throws IOException {
        OSAssert.assertNotMainThread();

        JsonNode node = result.getJsonResult();
        if (node.has("base")) {
            return JsonHelper.getJsonObjectReader().forType(MenuBase.class).readValue(node.path("base").traverse());
        } else {
            return new MenuBase();
        }
    }

    @Nonnull
    private Map<String, MenuAction> mActions = Collections.emptyMap();

    @Nullable
    private String mNextWindow;

    @Nonnull
    private Map<String, String> mWindow = Collections.emptyMap();

    @Nonnull
    public Map<String, String> getWindow() {
        return mWindow;
    }

    public void setWindow(@Nullable Map<String, String> window) {
        if (window == null) {
            mWindow = Collections.emptyMap();
        } else {
            mWindow = window;
        }
    }

    @Nullable
    public String getNextWindow() {
        return mNextWindow;
    }

    public void setNextWindow(@Nullable String nextWindow) {
        mNextWindow = nextWindow;
    }

    @Nonnull
    public Map<String, MenuAction> getActions() {
        return mActions;
    }

    public void setActions(@Nullable Map<String, MenuAction> actions) {
        if (actions == null) {
            mActions = Collections.emptyMap();
        } else {
            mActions = actions;

            // create copy of supplied actions
            for (Map.Entry<String, MenuAction> e : actions.entrySet()) {
                String actionName = e.getKey();
                MenuAction value = e.getValue();
                if (value != null) {
                    // update MenuAction with action name
                    value = value.withName(actionName);
                }
                if (value == null) {
                    if (mActions instanceof LinkedHashMap) {
                        ((LinkedHashMap<String, MenuAction>) mActions).put(actionName, null);
                    }
                } else {
                    mActions.put(actionName, value);
                }
            }
        }
    }

    @Nonnull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("actions", mActions).
                add("nextWindow", mNextWindow).
                add("window", mWindow).
                toString();
    }

}
