/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import android.view.View;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.actions.AbsAction;
import com.orangebikelabs.orangesqueeze.actions.ActionDialogBuilder;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.common.SimpleLoopingRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.concurrent.NotThreadSafe;

import androidx.core.text.HtmlCompat;

/**
 * @author tbsandee@orangebikelabs.com
 */
@NotThreadSafe
public class ContextMenuRequest extends SimpleLoopingRequest {
    /**
     * the menu title
     */
    final private String mMenuTitle;

    final private AbsMenuFragment mController;

    @Nonnull
    final protected View mAnchorView;

    final private Item mItem;

    /**
     * stores the menu items as they are retrieved, accessed from a Runnable (main thread) spawned from onFinishLoop()
     */
    final private List<MenuElement> mMenuItems = Collections.synchronizedList(new ArrayList<>());

    /**
     * unguarded, used only from request thread
     */
    private MenuBase mMenuBase;

    public ContextMenuRequest(AbsMenuFragment controller, View anchorView, Item item, String title) {
        super(SBContextProvider.get().getPlayerId());

        mMenuTitle = title;
        mController = controller;
        mAnchorView = anchorView;
        mItem = item;

        OSAssert.assertMainThread();

        setInitialBatchSize(-1);
    }

    @Override
    @Nonnull
    synchronized public ListenableFuture<Void> submit(ListeningExecutorService executor) {
        ListenableFuture<Void> retval = super.submit(executor);

        // set this request as the active context menu request, aborting requests that haven't happened yet
        mController.setActiveContextMenuRequest(this);
        return retval;
    }

    @OverridingMethodsMustInvokeSuper
    protected void onPopulateActionList(List<AbsAction<Item>> actionList) {
        boolean primaryPlayActionAllowed = true;
        for (MenuElement menuItem : mMenuItems) {
            if (StyleNames.ITEMNOACTION.equals(menuItem.getStyle())) {
                actionList.add(new ContextMenuAction(mContext, menuItem, null, null, true));
            } else {
                MenuAction action = MenuHelpers.getAction(menuItem, ActionNames.GO);
                if (action != null) {
                    Map<String, ?> params = MenuHelpers.buildItemParameters(menuItem, action);

                    // only add the item if the parameters have been filled out properly
                    if (params != null) {
                        actionList.add(new ContextMenuAction(mContext, menuItem, action, mMenuTitle, primaryPlayActionAllowed));

                        // only the first "play" item is taken over
                        if (action.isPlayAction()) {
                            primaryPlayActionAllowed = false;
                        }
                    }
                }
            }
        }
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected void finalizeRequest() throws SBRequestException {
        super.finalizeRequest();

        OSExecutors.getMainThreadExecutor().execute(() -> {
            if (!mController.isAdded() || !mController.isResumed() || !mController.isActiveContextMenuRequest(ContextMenuRequest.this)) {
                return;
            }
            List<AbsAction<Item>> actionList = new ArrayList<>();

            onPopulateActionList(actionList);
            ActionDialogBuilder<Item> builder = ActionDialogBuilder.newInstance(mController, mAnchorView);
            builder.setAvailableActions(actionList);
            builder.setShowPlayerSelection(true);

            if (builder.applies(mItem)) {
                if (!Strings.isNullOrEmpty(mMenuTitle)) {
                    String wrapped = mContext.getString(R.string.actionmenu_title_html, mMenuTitle);
                    builder.setTitle(HtmlCompat.fromHtml(wrapped, 0));
                }
                builder.create().show();
            }
        });
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected void onStartLoop(SBResult result) throws SBRequestException {
        super.onStartLoop(result);

        if (mMenuBase == null) {
            try {
                mMenuBase = MenuBase.get(result);
            } catch (IOException e) {
                throw new SBRequestException(e);
            }
        }
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected void onLoopItem(SBResult loopingResult, ObjectNode item) throws SBRequestException {
        try {
            MenuElement elem = MenuElement.get(item, mMenuBase);
            mMenuItems.add(elem);
        } catch (IOException e) {
            Reporting.report(e, "Error deserializing menu item", item);
        }
    }
}
