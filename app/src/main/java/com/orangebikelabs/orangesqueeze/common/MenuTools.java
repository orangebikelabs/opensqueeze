/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;

import javax.annotation.Nullable;

/**
 * Tools for android.view.Menu and android.view.MenuItem.
 */
public class MenuTools {
    @Nullable
    public static MenuItem setVisible(ContextMenu parentMenu, int id, boolean visible) {
        MenuItem item = parentMenu.findItem(id);
        if (item != null) {
            item.setVisible(visible);
        }
        return item;
    }

    @Nullable
    public static MenuItem setEnabled(ContextMenu parentMenu, int id, boolean enabled) {
        MenuItem item = parentMenu.findItem(id);
        if (item != null) {
            item.setEnabled(enabled);
        }
        return item;
    }

    @Nullable
    public static MenuItem setVisible(Menu parentMenu, int id, boolean visible) {
        MenuItem item = parentMenu.findItem(id);
        if (item != null) {
            item.setVisible(visible);
        }
        return item;
    }

    @Nullable
    public static MenuItem setEnabled(Menu parentMenu, int id, boolean enabled) {
        MenuItem item = parentMenu.findItem(id);
        if (item != null) {
            item.setEnabled(enabled);
        }
        return item;
    }
}
