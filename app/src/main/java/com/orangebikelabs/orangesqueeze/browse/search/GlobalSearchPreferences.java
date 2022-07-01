/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.menu.MenuAction;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class GlobalSearchPreferences {

    final static private Object sLock = new Object();

    @Nullable
    static private Set<String> sWhitelist;

    private static Set<String> getWhitelist() {
        synchronized (sLock) {
            if (sWhitelist == null) {
                Set<String> val = Collections.synchronizedSet(new LinkedHashSet<>());
                val.addAll(SBPreferences.get().getGlobalSearchAutoExpandSet());
                sWhitelist = val;
            }
            return sWhitelist;
        }
    }

    public static boolean shouldExpandItem(MenuElement menuElement, MenuAction goAction) {

        String key = getItemKey(menuElement, goAction);
        if (key == null) {
            return false;
        }

        return getWhitelist().contains(key);
    }

    public static String getAutoExpandKey(MenuElement menuElement, @Nullable MenuAction goAction) {
        return getItemKey(menuElement, goAction);
    }

    public static void setAutoExpandItem(String autoExpandKey, boolean expand) {
        if (expand) {
            getWhitelist().add(autoExpandKey);
        } else {
            getWhitelist().remove(autoExpandKey);
        }
        SBPreferences.get().setGlobalSearchAutoExpandSet(getWhitelist());
    }

    public static int getMaxRows() {
        return 6;
    }

    @Nullable
    private static String getItemKey(MenuElement element, @Nullable MenuAction action) {
        if (action == null) {
            return null;
        }
        String id = action.getParams().get("item_id");
        if (id == null) {
            return null;
        }

        int ndx = id.indexOf(".");

        String retval = element.getText() + ":" + id.substring(ndx + 1);
        return retval;
    }
}
