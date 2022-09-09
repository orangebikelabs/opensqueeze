/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.actions.ActionDialogBuilder;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.browse.common.AbsItemAction;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequest;
import com.orangebikelabs.orangesqueeze.browse.common.CommandTools;
import com.orangebikelabs.orangesqueeze.browse.common.IconRetriever;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.browse.common.ItemBaseAdapter.ViewHolder;
import com.orangebikelabs.orangesqueeze.browse.common.ItemType;
import com.orangebikelabs.orangesqueeze.common.NavigationCommandSet;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.ThreadTools;
import com.orangebikelabs.orangesqueeze.download.DownloadAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import androidx.core.text.HtmlCompat;
import arrow.core.Option;
import arrow.core.OptionKt;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class StandardMenuItem extends Item {

    final static private ImmutableList<IconRetriever> sIconRetrievers;

    static {
        sIconRetrievers = ImmutableList.of(MenuElement.newIconRetriever());
    }

    static public StandardMenuItem newInstance(BrowseRequest request, JsonNode node, MenuElement element) {
        if (element.isTrack() && isSimpleTrackItem(request, node, element)) {
            return new TrackMenuItem(node, element);
        } else if (SimpleImageItem.isSimpleImageItem(node)) {
            return new SimpleImageItem(node, element);
        } else {
            return new StandardMenuItem(node, element, false);
        }
    }

    static public boolean isSimpleTrackItem(BrowseRequest request, JsonNode node, MenuElement element) {
        boolean retval = false;
        if (CommandTools.commandMatches(request.getCommands(), "browselibrary", "items")) {
            retval = true;
        } else if (CommandTools.commandMatches(request.getCommands(), "custombrowse", "browsejive")) {
            retval = true;
        } else if (CommandTools.commandMatches(request.getCommands(), "tracks")) {
            retval = true;
        }
        return retval;
    }

    @Nonnull
    final protected MenuElement mMenuElement;

    @GuardedBy("this")
    @Nullable
    protected ItemType mType;

    @Nonnull
    final protected JsonNode mJson;

    final protected boolean mForHome;

    @GuardedBy("this")
    @Nullable
    protected String mSectionName;

    @GuardedBy("this")
    @Nullable
    private Boolean mMutatedCheckboxChecked;

    @GuardedBy("this")
    protected boolean mMutatedProgressVisible;

    @GuardedBy("this")
    protected int mMutatedSliderValue;

    protected StandardMenuItem(JsonNode json, MenuElement element, boolean forHome) {
        mJson = json;
        mMenuElement = element;
        mMutatedSliderValue = element.getSliderInitialValue() - element.getSliderMinValue();

        // work around issue where dynamic style='itemnoaction" is interepreted as readonly
        //mEnabled = true;
        //mEnabled = !StyleNames.ITEMNOACTION.equals(mMenuElement.getStyle());
        mForHome = forHome;
    }

    @Nonnull
    @Override
    public JsonNode getNode() {
        return mJson;
    }

    @Nonnull
    @Override
    public String getText() {
        return mMenuElement.getText();
    }

    @Override
    public String getSectionName() {
        synchronized (this) {
            if (mSectionName == null) {
                String textKey = mMenuElement.getTextkey();
                if (textKey != null) {
                    mSectionName = getAlphabeticSection(textKey);
                } else {
                    // special case for VA at front of list
                    if (mMenuElement.isVariousArtist()) {
                        mSectionName = "";
                    }
                }
            }
            return mSectionName;
        }
    }

    synchronized public void setMutatedSliderValue(int value) {
        mMutatedSliderValue = value;
    }

    synchronized public int getMutatedSliderValue() {
        return mMutatedSliderValue;
    }

    synchronized public void setMutatedProgressVisible(boolean value, @Nullable final ProgressBar progressBar) {
        mMutatedProgressVisible = value;
        if (progressBar == null) {
            return;
        }

        // support running this on the main thread or background thread
        Runnable r = () -> progressBar.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        if (!ThreadTools.isMainThread()) {
            OSExecutors.getMainThreadExecutor().execute(r);
        } else {
            r.run();
        }
    }

    synchronized public boolean getMutatedProgressVisible() {
        return mMutatedProgressVisible;
    }

    @Nullable
    synchronized public Boolean getMutatedCheckboxChecked() {
        OSAssert.assertMainThread();
        return mMutatedCheckboxChecked;
    }

    synchronized public void setMutatedCheckboxChecked(@Nullable Boolean value) {
        OSAssert.assertMainThread();
        mMutatedCheckboxChecked = value;
    }

    @Nonnull
    @Override
    public ImmutableList<IconRetriever> getIconRetrieverList() {
        return sIconRetrievers;
    }

    @Nonnull
    @Override
    synchronized public ItemType getBaseType() {
        if (mType == null) {
            mType = calculateType();
        }
        return mType;
    }

    @Nonnull
    public MenuElement getMenuElement() {
        return mMenuElement;
    }

    /**
     * called from StandardItemListAdapter to optionally do view-level bindings beyond what the defaults are
     */
    public void prepare(ViewGroup parentView, ViewHolder viewHolder, ThumbnailProcessor processor) {
    }

    @Nonnull
    public String getText1() {
        return mMenuElement.getText1();
    }

    @Nonnull
    public Option<String> getText2() {
        return Option.fromNullable(mMenuElement.getText2());
    }

    @Nonnull
    public Option<String> getText3() {
        return OptionKt.none();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isSingleItemConsideredEmpty() {
        return getBaseType() == ItemType.IVT_TEXT;
    }

    public boolean showContextMenu(AbsMenuFragment fragment, View anchorView) {
        boolean handled = false;
        OSAssert.assertMainThread();

        Context context = fragment.requireActivity();
        MenuAction moreAction = MenuHelpers.getAction(mMenuElement, ActionNames.MORE);
        if (moreAction != null && !moreAction.getCommands().isEmpty()) {
            List<String> params = MenuHelpers.buildParametersAsList(mMenuElement, moreAction, true);
            if (params != null) {
                ItemContextMenuRequest request = new ItemContextMenuRequest(fragment, anchorView, this);
                request.setCommands(moreAction.getCommands());
                request.setParameters(params);
                request.submit(OSExecutors.getUnboundedPool());
                handled = true;
            }
        } else {
            // no "more" action
            List<AbsItemAction> actionList = new ArrayList<>();

            // use alternate way to get context menu, for old servers, etc.
            for (MenuAction action : ContextMenuAction.secondaryActionList(mMenuElement)) {
                actionList.add(new ContextMenuAction(context, mMenuElement, action, getItemTitle(), true));
            }

            // add download action candidate
            actionList.add(new DownloadAction(context));

            ActionDialogBuilder<Item> builder = ActionDialogBuilder.newInstance(fragment, anchorView);
            builder.setAvailableActions(actionList);
            builder.setShowPlayerSelection(true);

            if (builder.applies(this)) {
                String title = getItemTitle();
                if (!Strings.isNullOrEmpty(title)) {
                    String wrappedTitle = context.getString(R.string.actionmenu_title_html, title);
                    builder.setTitle(HtmlCompat.fromHtml(wrappedTitle, 0));
                }
                builder.create().show();
                handled = true;
            }
        }
        return handled;
    }

    static final public String ALPHABETIC_SECTION_STRING = " #ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final private char[] ALPHABETIC_SECTION_CHARS = ALPHABETIC_SECTION_STRING.toCharArray();

    /**
     * Retrieve the section name when using alphabetic sections
     */
    @Nullable
    protected String getAlphabeticSection(String text) {
        if (text.length() == 0) {
            return null;
        }

        char firstChar = Character.toUpperCase(text.charAt(0));
        int retval;
        // is it outside of the specified range?
        if (firstChar >= 'A' && firstChar <= 'Z') {
            retval = ALPHABETIC_SECTION_CHARS[firstChar - 'A' + 2];
        } else {
            if (Character.isDigit(firstChar)) {
                retval = ALPHABETIC_SECTION_CHARS[1];
            } else {
                retval = ALPHABETIC_SECTION_CHARS[0];
            }
        }
        return ((char) retval) + "";
    }

    public boolean isNode() {
        return getMenuElement().isANode();
    }

    @Nonnull
    protected ItemType calculateType() {
        ItemType retval;
        if (mMenuElement.isCheckbox()) {
            retval = ItemType.IVT_CHECKBOX;
        } else if (mMenuElement.isRadio()) {
            retval = ItemType.IVT_RADIO;
        } else if (mMenuElement.isArtist()) {
            retval = ItemType.IVT_ARTIST;
        } else if (mMenuElement.isChoice()) {
            retval = ItemType.IVT_CHOICE;
        } else if (mMenuElement.isSlider()) {
            retval = ItemType.IVT_SLIDER;
        } else if (mMenuElement.isYear()) {
            retval = ItemType.IVT_YEAR;
        } else if (StyleNames.ITEMNOACTION.equals(mMenuElement.getStyle())) {
            retval = ItemType.IVT_TEXT;
        } else {
            if (mMenuElement.getText2() != null) {
                retval = ItemType.IVT_THUMBTEXT2;
            } else {
                retval = ItemType.IVT_THUMBTEXT;
            }
        }
        return retval;
    }

    @Nullable
    @Override
    public NavigationItem getNavigationItem() {
        NavigationItem retval = null;

        String nodeId = getMenuElement().getId();
        if (isNode()) {
            retval = NavigationItem.Companion.newBrowseNodeItem(getItemTitle(), nodeId, mForHome, null);
        } else {
            MenuAction action = getMenuElement().getActions().get(ActionNames.DO);
            if (action == null) {
                action = getMenuElement().getActions().get(ActionNames.GO);
            }
            if (action != null) {
                List<String> params = new ArrayList<>();
                for (Map.Entry<String, String> it : action.getParams().entrySet()) {
                    String val = it.getKey() + ":" + it.getValue();
                    params.add(val);
                }

                retval = NavigationItem.Companion.newBrowseRequestItem(
                        getItemTitle(),
                        new NavigationCommandSet(
                                action.getCommands(),
                                params
                        ),
                        nodeId,
                        null,
                        null,
                        null,
                        mForHome,
                        null
                );
            } else {
                OSLog.e("Unsupported handler for root menu item: " + getMenuElement());
            }
        }
        return retval;
    }
}
