/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.menu;

import android.content.Context;

import androidx.fragment.app.Fragment;

import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.actions.AbsAction;
import com.orangebikelabs.orangesqueeze.browse.common.AbsItemAction;
import com.orangebikelabs.orangesqueeze.browse.common.Item;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@NotThreadSafe
public class ContextMenuAction extends AbsItemAction {
    /**
     * retrieve action list whenever the "more" action isn't available
     */
    @Nonnull
    public static Set<MenuAction> secondaryActionList(MenuElement menuItem) {
        Set<MenuAction> retval = new LinkedHashSet<>();
        Map<String, MenuAction> actions = new HashMap<>();
        actions.putAll(menuItem.getBaseActions());
        actions.putAll(menuItem.getActions());

        for (Map.Entry<String, MenuAction> entry : actions.entrySet()) {
            String actionName = entry.getKey();

            // skip "GO" and "DO" for context menu generation
            if (actionName.equals(ActionNames.GO) || actionName.equals(ActionNames.DO)) {
                continue;
            }

            MenuAction action = entry.getValue();
            if (action == null) {
                continue;
            }

            Map<String, ?> params = MenuHelpers.buildItemParameters(menuItem, action);
            // only add the item if the parameters have been filled out properly
            if (params == null || action.getCommands().isEmpty()) {
                continue;
            }

            retval.add(action);
        }
        return retval;
    }

    @Nonnull
    final private MenuElement mMenuElement;

    @Nullable
    final private MenuAction mMenuAction;
    final private boolean mItemDisabled;
    final private String mItemTitle;

    @Nullable
    final private String mNextWindow;

    private int mIconRid;
    private String mText;

    final private long mOrder;

    public ContextMenuAction(Context context, MenuElement element, @Nullable MenuAction action, @Nullable String rootMenuItemTitle,
                             boolean primaryPlayActionAllowed) {
        super(context, 0, 0);

        mMenuElement = element;
        mMenuAction = action;

        String textOverride = null;
        String nextWindowOverride = null;

        long order = mCreationOrder;
        mText = element.getText1();

        if (action == null) {
            mItemDisabled = true;
        } else {
            mItemDisabled = false;
            if (action.isPlayAction() && primaryPlayActionAllowed) {
                mIconRid = R.drawable.ic_play_arrow;
                textOverride = context.getString(R.string.play_desc);
                order = -5;
                nextWindowOverride = NextWindowNames.NOWPLAYING;
            } else if (action.isPlayNextAction()) {
                mIconRid = R.drawable.ic_skip_next;
                textOverride = context.getString(R.string.playnext_desc);
                order = -4;
                nextWindowOverride = NextWindowNames.NOWPLAYING;
            } else if (action.isAddAction()) {
                mIconRid = R.drawable.ic_add;
                textOverride = context.getString(R.string.playadd_desc);
                order = -3;
                nextWindowOverride = NextWindowNames.NOREFRESH;
            } else if (action.isAddFavoriteAction()) {
                mIconRid = R.drawable.ic_favorite;
                textOverride = context.getString(R.string.action_addfavorite);
                order = -2;
            } else if (action.isRemoveFavoriteAction()) {
                mIconRid = R.drawable.ic_clear;
                textOverride = context.getString(R.string.action_removefavorite);
                order = -2;
            } else if (action.isRemoveFromPlaylistAction()) {
                mIconRid = R.drawable.ic_clear;
            } else if (action.isBrowseAction(mMenuElement)) {
                mIconRid = R.drawable.ic_browse;
            } else if (action.isRandomPlayAction()) {
                mIconRid = R.drawable.ic_shuffle;
            } else if (action.isPlayAction()) {
                // secondary play action, just supply icon
                mIconRid = R.drawable.ic_play_arrow;
                nextWindowOverride = NextWindowNames.NOWPLAYING;
            }
        }
        mOrder = order;

        String effectiveItemTitle;
        if (textOverride != null) {
            mText = textOverride;
            effectiveItemTitle = rootMenuItemTitle;
        } else {
            // if we haven't already set the item title, build it from the two relevant menu items
            String contextItemText = mText;
            if (contextItemText.contains(":")) {
                // we (naively?) assume that if a colon is in the title, it already has relevant context info
                effectiveItemTitle = contextItemText;
            } else {
                if (Strings.isNullOrEmpty(rootMenuItemTitle)) {
                    effectiveItemTitle = contextItemText;
                } else {
                    // build title from both parent menu ("Air") and submenu ("Browse by selected") to form Air: Browse by selected
                    effectiveItemTitle = context.getString(R.string.contextmenu_item_title, rootMenuItemTitle, contextItemText);
                }
            }
        }
        mItemTitle = effectiveItemTitle;
        mNextWindow = nextWindowOverride;
    }

    @Override
    public int getIconRid() {
        return mIconRid;
    }

    @Override
    @Nonnull
    public String toString() {
        return mText;
    }

    @Override
    public int compareTo(AbsAction<Item> another) {
        if (another instanceof ContextMenuAction) {
            ContextMenuAction anotherAction = (ContextMenuAction) another;
            return Longs.compare(mOrder, anotherAction.mOrder);
        } else {
            return super.compareTo(another);
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        ContextMenuAction that = (ContextMenuAction) o;

        if (mIconRid != that.mIconRid) {
            return false;
        }
        if (mItemDisabled != that.mItemDisabled) {
            return false;
        }
        if (mOrder != that.mOrder) {
            return false;
        }
        if (mItemTitle != null ? !mItemTitle.equals(that.mItemTitle) : that.mItemTitle != null) {
            return false;
        }
        if (mMenuAction != null ? !mMenuAction.equals(that.mMenuAction) : that.mMenuAction != null) {
            return false;
        }
        if (!mMenuElement.equals(that.mMenuElement)) {
            return false;
        }
        if (mNextWindow != null ? !mNextWindow.equals(that.mNextWindow) : that.mNextWindow != null) {
            return false;
        }
        if (mText != null ? !mText.equals(that.mText) : that.mText != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + mMenuElement.hashCode();
        result = 31 * result + (mMenuAction != null ? mMenuAction.hashCode() : 0);
        result = 31 * result + (mItemDisabled ? 1 : 0);
        result = 31 * result + (mItemTitle != null ? mItemTitle.hashCode() : 0);
        result = 31 * result + (mNextWindow != null ? mNextWindow.hashCode() : 0);
        result = 31 * result + mIconRid;
        result = 31 * result + (mText != null ? mText.hashCode() : 0);
        result = 31 * result + (int) (mOrder ^ (mOrder >>> 32));
        return result;
    }

    @Override
    public boolean initialize(Item item) {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !mItemDisabled;
    }

    @Override
    public boolean execute(Fragment controller) {
        ((AbsMenuFragment) controller).execute(mItemTitle, mMenuElement, mMenuAction, mNextWindow, true, null);
        return true;
    }
}
