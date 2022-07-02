/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.actions;

import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.ListPopupWindow;
import android.view.View;

import com.orangebikelabs.orangesqueeze.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class ActionDialogBuilder<T> {

    @Nonnull
    public static <T> ActionDialogBuilder<T> newInstance(Fragment controller, View anchorView) {
        return new ActionDialogBuilder<>(controller, anchorView);
    }

    final private ArrayList<AbsAction<T>> mApplicableActions = new ArrayList<>();

    @Nonnull
    private List<? extends AbsAction<T>> mAvailableActions = Collections.emptyList();

    @Nonnull
    private Fragment mController;

    @Nullable
    private CharSequence mTitle;

    private boolean mShowPlayerSelection;

    @Nonnull
    private View mAnchorView;

    private ActionDialogBuilder(Fragment controller, View anchorView) {
        mController = controller;
        mAnchorView = anchorView;
    }

    public void setAvailableActions(List<? extends AbsAction<T>> actions) {
        mAvailableActions = new ArrayList<>(actions);
        Collections.sort(mAvailableActions);
    }

    public boolean applies(T item) {
        mApplicableActions.clear();

        for (AbsAction<T> a : mAvailableActions) {
            if (a.initialize(item)) {
                mApplicableActions.add(a);
            }
        }
        return !mApplicableActions.isEmpty();
    }

    public void setTitle(@Nullable CharSequence title) {
        mTitle = title;
    }

    public void setShowPlayerSelection(boolean showPlayerSelection) {
        mShowPlayerSelection = showPlayerSelection;
    }

    @Nonnull
    public ListPopupWindow create() {
        final ListPopupWindow retval = new ListPopupWindow(mController.requireContext());
        retval.setAnchorView(mAnchorView);
        ActionListAdapter<T> adapter = new ActionListAdapter<>(mController.requireContext(), mApplicableActions);
        retval.setAdapter(adapter);
        retval.setModal(true);
        retval.setOnItemClickListener((parent, view, position, id) -> {
            retval.dismiss();
            AbsAction<?> action = (AbsAction<?>) parent.getAdapter().getItem(position);
            action.execute(mController);
        });
        retval.setContentWidth(mController.requireContext().getResources().getDimensionPixelSize(R.dimen.popup_list_window_width));
        return retval;
    }
}