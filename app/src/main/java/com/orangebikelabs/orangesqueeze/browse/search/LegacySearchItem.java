/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.browse.search;

import android.view.View;

import com.fasterxml.jackson.databind.JsonNode;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.actions.ActionDialogBuilder;
import com.orangebikelabs.orangesqueeze.browse.common.AbsItemAction;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;
import com.orangebikelabs.orangesqueeze.menu.AbsMenuFragment;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

import androidx.core.text.HtmlCompat;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class LegacySearchItem extends StandardMenuItem {
    final private ItemType mType;

    @GuardedBy("this")
    protected String mText;

    public LegacySearchItem(JsonNode json, MenuElement menuElement, String sectionName, ItemType type) {
        super(json, menuElement, false);

        mSectionName = sectionName;
        mType = type;
    }

    @Nonnull
    @Override
    public String getText() {
        return getText1();
    }

    @Nonnull
    @Override
    synchronized public String getText1() {
        if (mText == null) {
            if (mJson.has("contributor")) {
                mText = mJson.get("contributor").asText();
            } else if (mJson.has("album")) {
                mText = mJson.get("album").asText();
            } else if (mJson.has("track")) {
                mText = mJson.get("track").asText();
            } else {
                mText = "undefined";
            }
        }
        return mText;
    }

    @Override
    synchronized public String getSectionName() {
        return mSectionName;
    }

    @Nonnull
    @Override
    public ItemType getBaseType() {
        return mType;
    }

    @Override
    public boolean showContextMenu(AbsMenuFragment fragment, View itemView) {
        boolean handled = false;
        List<AbsItemAction> actionList = AbsItemAction.getContextActionCandidates(fragment.requireActivity());

        ActionDialogBuilder<Item> builder = ActionDialogBuilder.newInstance(fragment, itemView);
        builder.setAvailableActions(actionList);
        builder.setShowPlayerSelection(true);

        if (builder.applies(this)) {
            String title = fragment.getString(R.string.actionmenu_title_html, getItemTitle());
            builder.setTitle(HtmlCompat.fromHtml(title, 0));
            builder.create().show();
            handled = true;
        }
        return handled;
    }
}
