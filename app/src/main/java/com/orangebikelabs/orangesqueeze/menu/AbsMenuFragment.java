/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.menu;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.util.concurrent.Futures;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.SBFragment;
import com.orangebikelabs.orangesqueeze.app.DrawerActivity;
import com.orangebikelabs.orangesqueeze.browse.common.AbsItemAction;
import com.orangebikelabs.orangesqueeze.browse.common.AddToPlaylistAction;
import com.orangebikelabs.orangesqueeze.browse.common.CommandTools;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.browse.common.PlayNextAction;
import com.orangebikelabs.orangesqueeze.browse.common.PlayNowAction;
import com.orangebikelabs.orangesqueeze.common.AbsFragmentResultReceiver;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.NavigationCommandSet;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.NavigationManager;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayOptions;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.SBRequest.Type;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.ui.MainActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import androidx.core.text.HtmlCompat;

/**
 * Fragment ancestor that provides methods for navigating and responding to item clicks.
 *
 * @author tsandee
 */
abstract public class AbsMenuFragment extends SBFragment {

    public enum SnackbarLength {LONG, SHORT}

    /**
     * the last context menu request for this fragment
     */
    @Nullable
    protected WeakReference<ContextMenuRequest> mCurrentContextMenuRequest;

    /**
     * horizontal gauge that shows progress of loading list items
     */
    protected ProgressBar mLoadingProgress;

    // we store the last snackbar so we can cancel it if necessary
    @Nullable
    private Toast mLastSnackbar;

    @Nullable
    public PlayerId getFillPlayerId() {
        return null;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mLoadingProgress = view.findViewById(R.id.listload_progress);
        setLoadProgress(1, 100);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mLastSnackbar = null;
    }

    public void execute(String itemTitle, MenuElement element, @Nullable MenuAction action, @Nullable String nextWindow, boolean fromContextMenu,
                        @Nullable String fillValue) {

        if (element.getInput() != null) {
            // if it's an input query, pop up the dialog box
            MenuInputDialog.newInstance(this, element, action).show();
        } else if (action != null) {
            if (nextWindow == null) {
                nextWindow = MenuHelpers.getNextWindow(element, action);
            }
            if (element.getCheckbox() != null && nextWindow == null) {
                nextWindow = NextWindowNames.NOREFRESH;
            }
            if (!action.getCommands().isEmpty()) {
                List<String> params = MenuHelpers.buildParametersAsList(element, action, false);
                if (params != null) {
                    if (element.getShowBigArtwork() != 0) {
                        NavigationCommandSet ncs = new NavigationCommandSet(action.getCommands(), params);
                        executeShowBigArtwork(itemTitle, ncs);
                    } else {
                        // execute the command directly
                        executeMenuCommand(itemTitle, element, action, params, nextWindow, fromContextMenu, fillValue);
                    }
                }
            } else if (element.getSelectedIndex().isPresent() && !action.getChoices().isEmpty()) {
                int ndx = element.getSelectedIndex().get();
                if (ndx >= 0 && ndx < action.getChoices().size()) {
                    // advance the selection
                    // TODO temporary behavior, use a dialog?
                    ndx++;
                    if (ndx >= action.getChoices().size()) {
                        ndx = 0;
                    }
                    MenuChoice choice = action.getChoices().get(ndx);
                    executeRawMenuCommand(itemTitle, choice.getCommands(), Collections.emptyList(), nextWindow, fromContextMenu, fillValue);

                    // selected indices are 1-based, not 0-based
                    element.setSelectedIndex(ndx + 1);
                } else {
                    OSLog.i("Cannot process choice block for action: " + action);
                }
            }
        } else if (!element.getSubelementList().isEmpty()) {
            List<MenuElementHolder> choices = new ArrayList<>();
            for (MenuElement elem : element.getSubelementList()) {
                choices.add(new MenuElementHolder(elem.getText(), elem));
            }

            CharSequence[] adaptedItems = new CharSequence[choices.size()];
            for (int i = 0; i < adaptedItems.length; i++) {
                adaptedItems[i] = choices.get(i).toString();
            }

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setTitle(R.string.choose)
                    .setItems(adaptedItems, (dialog, ndx) -> {
                        MenuElementHolder item = choices.get(ndx);

                        MenuAction action1 = MenuHelpers.getAction(item.mElement, ActionNames.GO);
                        execute(item.mText, item.mElement, action1, null, false, null);
                    })
                    .setCancelable(true)
                    .show();
        } else if (element.getWeblink() != null) {
            String uri = element.getWeblink();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            startActivity(intent);
        }
    }

    protected void populatePersistentExtras(Intent intent) {
        // intentionally blank
    }

    public void executeCommandSet(@Nonnull NavigationItem sourceItem, @Nonnull NavigationCommandSet commandSet, @Nullable String nextWindow) {
        String title = sourceItem.getName();
        executeRawMenuCommand(title, commandSet.getCommands(), commandSet.getParameters(), nextWindow, false, null);
    }

    public void executeMenuCommand(String title, MenuElement element, MenuAction action, List<String> params, @Nullable String nextWindow, boolean fromContextMenu,
                                   @Nullable String fillValue) {
        boolean hijacked = hijackCommand(title, action.getCommands(), params, nextWindow, fromContextMenu);
        if (hijacked) {
            return;
        }

        if (nextWindow == null && !Objects.equal(action.getName(), ActionNames.DO)) {
            NavigationCommandSet requestCommandSet = new NavigationCommandSet(action.getCommands(), params);
            NavigationCommandSet addCommandSet = NavigationCommandSet.Companion.buildCommandSet(element, ActionNames.ADD);
            if (Objects.equal(addCommandSet, requestCommandSet)) {
                addCommandSet = null;
            }
            NavigationCommandSet playCommandSet = NavigationCommandSet.Companion.buildCommandSet(element, ActionNames.PLAY);
            if (Objects.equal(playCommandSet, requestCommandSet)) {
                playCommandSet = null;
            }
            NavigationCommandSet playNextCommandSet = NavigationCommandSet.Companion.buildCommandSet(element, ActionNames.ADDHOLD);
            if (Objects.equal(playNextCommandSet, requestCommandSet)) {
                playNextCommandSet = null;
            }

            // only do menu browse if action name isn't "do"
            executeMenuBrowse(title, requestCommandSet, addCommandSet, playCommandSet, playNextCommandSet);
        } else {
            // just execute the command, don't open a window
            executeRawMenuCommand(title, action.getCommands(), params, nextWindow, fromContextMenu, fillValue);
        }
    }

    public synchronized boolean isActiveContextMenuRequest(ContextMenuRequest request) {
        return mCurrentContextMenuRequest != null && mCurrentContextMenuRequest.get() == request;
    }

    public synchronized void setActiveContextMenuRequest(ContextMenuRequest request) {
        ContextMenuRequest oldRequest = mCurrentContextMenuRequest == null ? null : mCurrentContextMenuRequest.get();
        if (oldRequest != null) {
            oldRequest.stop();
        }
        mCurrentContextMenuRequest = new WeakReference<>(request);
    }

    public void setLoadProgress(int progress, int max) {
        if (mLoadingProgress != null) {
            mLoadingProgress.setProgress(progress);
            mLoadingProgress.setMax(max);

            // hide progress once it's completed
            mLoadingProgress.setVisibility(progress == max ? View.INVISIBLE : View.VISIBLE);
        }
    }

    public void showSnackbar(CharSequence text, SnackbarLength length) {
        if (mLastSnackbar != null) {
            mLastSnackbar.cancel();
        }
        mLastSnackbar = null;

        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || !isAdded()) {
            return;
        }
        mLastSnackbar = Toast.makeText(getContext(), text, length == SnackbarLength.LONG ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastSnackbar.show();
    }

    @Nullable
    abstract protected View getSnackbarView();

    protected void executeRawMenuCommand(String title, List<String> commands, List<String> params, @Nullable String nextWindow,
                                         boolean fromContextMenu, @Nullable String fillValue) {
        // TODO address out-of-order request completion and lingering progress indicator
        setShowProgressIndicator(true);

        ArrayList<String> cmds = new ArrayList<>(commands);
        for (String p : params) {
            if (fillValue != null && p.startsWith("valtag:")) {
                String tag = p.substring(p.indexOf(':') + 1);
                cmds.add(tag + ":" + fillValue);
            } else {
                cmds.add(p);
            }
        }

        SBRequest request = mSbContext.newRequest(Type.COMET, cmds);

        CharSequence toastMessage = CommandTools.lookupToast(requireContext(), request, title);

        // use effective player id for commands that we execute
        request.setPlayerId(getEffectivePlayerId());

        FutureResult futureResult = request.submit(OSExecutors.getCommandExecutor());
        Futures.addCallback(futureResult, new CommandResultReceiver(this, toastMessage, fromContextMenu, nextWindow), OSExecutors.getMainThreadExecutor());
    }

    static class CommandResultReceiver extends AbsFragmentResultReceiver<AbsMenuFragment> {
        @Nullable
        final private CharSequence mToastMessage;
        final private boolean mFromContextMenu;

        @Nullable
        final private String mNextWindow;

        CommandResultReceiver(AbsMenuFragment fragment, @Nullable CharSequence toastMessage, boolean fromContextMenu, @Nullable String nextWindow) {
            super(fragment);

            mToastMessage = toastMessage;
            mFromContextMenu = fromContextMenu;
            mNextWindow = nextWindow;
        }

        @Override
        public void onEventualError(AbsMenuFragment fragment, @Nullable Throwable e) {
            super.onEventualError(fragment, e);

            fragment.setShowProgressIndicator(false);
        }

        @Override
        public void onEventualSuccess(AbsMenuFragment fragment, SBResult result) {
            if (mToastMessage != null) {
                fragment.showSnackbar(mToastMessage, SnackbarLength.LONG);
            }
            fragment.setShowProgressIndicator(false);
            if (NextWindowNames.GRANDPARENT.equals(mNextWindow)) {
                if (mFromContextMenu) {
                    fragment.finishForNextWindow(1, true);
                } else {
                    fragment.finishForNextWindow(2, true);
                }
            } else if (NextWindowNames.NOWPLAYING.equals(mNextWindow)) {
                NavigationManager manager = fragment.getNavigationManager();
                manager.startActivity(manager.newNowPlayingIntent());
            } else if (NextWindowNames.PARENT.equals(mNextWindow)) {
                if (mFromContextMenu) {
                    // from context menu, act as refresh
                    fragment.requery(null);
                } else {
                    fragment.finishForNextWindow(1, true);
                }
            } else if (NextWindowNames.PARENTNOREFRESH.equals(mNextWindow)) {
                if (!mFromContextMenu) {
                    fragment.finishForNextWindow(1, false);
                }
            } else if (NextWindowNames.REFRESH.equals(mNextWindow)) {
                fragment.requery(null);
            } else if (NextWindowNames.NOREFRESH.equals(mNextWindow) || mNextWindow == null) {
                // do nothing
            } else if (NextWindowNames.REFRESHORIGIN.equals(mNextWindow)) {
                fragment.finishForNextWindow(1, true);
            } else if (NextWindowNames.HOME.equals(mNextWindow) || NextWindowNames.MYMUSIC.equals(mNextWindow)) {
                fragment.startActivity(MainActivity.Companion.newIntent(fragment.requireContext()));
            } else {
                Reporting.report(null, "Unhandled next window flag: " + mNextWindow, result);
            }
        }
    }

    @Nullable
    protected PlayerId getEffectivePlayerId() {
        PlayerId fillPlayerId = getFillPlayerId();
        if (fillPlayerId != null) {
            return fillPlayerId;
        } else {
            return mSbContext.getPlayerId();
        }
    }

    @Nonnull
    protected Bundle getMutableArguments() {
        return new Bundle();
    }

    public void requery(@Nullable Bundle newArgs) {
        // default is to do nothing
    }

    public void executeShowBigArtwork(String title, String artworkUrl) {
        NavigationManager manager = getNavigationManager();
        try {
            Intent intent = manager.newShowArtworkIntent(title, artworkUrl, getMutableArguments());
            manager.startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.in_from_right, android.R.anim.fade_out);
        } catch (IllegalStateException e) {
            String message = MoreObjects.toStringHelper("Error browsing to artwork")
                    .add("mutableArgs", getMutableArguments())
                    .add("currentIntent", requireActivity().getIntent())
                    .toString();
            Reporting.report(e, message, null);
        }
    }

    public void executeShowBigArtwork(String title, NavigationCommandSet commandSet) {
        NavigationManager manager = getNavigationManager();
        try {
            Intent intent = manager.newShowArtworkIntent(title, commandSet, getMutableArguments());
            manager.startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.in_from_right, android.R.anim.fade_out);
        } catch (IllegalStateException e) {
            String message = MoreObjects.toStringHelper("Error browsing to artwork")
                    .add("mutableArgs", getMutableArguments())
                    .add("currentIntent", requireActivity().getIntent())
                    .toString();
            Reporting.report(e, message, null);
        }
    }

    public void executeMenuBrowse(NavigationItem navigationItem) {
        NavigationManager manager = getNavigationManager();
        try {
            Intent intent = manager.newBrowseRequestIntent(navigationItem);
            populatePersistentExtras(intent);
            manager.startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.in_from_right, android.R.anim.fade_out);
        } catch (IllegalStateException e) {
            String message = MoreObjects.toStringHelper("Error browsing to menu")
                    .add("mutableArgs", getMutableArguments())
                    .add("currentIntent", requireActivity().getIntent())
                    .toString();
            Reporting.report(e, message, null);
        }
    }

    public void executeMenuBrowse(String title, NavigationCommandSet requestCommandSet,
                                  @Nullable NavigationCommandSet addCommandSet, @Nullable NavigationCommandSet playCommandSet, @Nullable NavigationCommandSet playNextCommandSet) {
        NavigationManager manager = getNavigationManager();
        try {
            Intent intent = manager.newBrowseRequestIntent(title, requestCommandSet, addCommandSet, playCommandSet, playNextCommandSet, getFillPlayerId(), getMutableArguments());
            populatePersistentExtras(intent);
            manager.startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.in_from_right, android.R.anim.fade_out);
        } catch (IllegalStateException e) {
            String message = MoreObjects.toStringHelper("Error browsing to menu")
                    .add("mutableArgs", getMutableArguments())
                    .add("currentIntent", requireActivity().getIntent())
                    .toString();
            Reporting.report(e, message, null);
        }
    }

    public void executeDefaultSelectionAction(View itemView, Item item) {
        PlayOptions playMode = SBPreferences.get().getTrackSelectPlayMode();

        AbsItemAction action = null;
        switch (playMode) {
            case ADD:
                action = new AddToPlaylistAction(requireContext());
                break;
            case INSERT:
                action = new PlayNextAction(requireContext());
                break;
            case PLAY:
                action = new PlayNowAction(requireContext());
                break;
            case PROMPT:
                showActionMenu(item, itemView.findViewById(R.id.action_button));
                break;
        }
        if (action != null) {
            action.initialize(item);
            action.execute(this);
        }
    }

    protected void finishForNextWindow(int numberOfPos, boolean refresh) {
        DrawerActivity activity = (DrawerActivity) requireActivity();
        Intent intent = new Intent();
        intent.putExtra(NavigationManager.EXTRA_TARGET_LEVEL, activity.getCurrentItemLevel() - numberOfPos);
        activity.setResult(NavigationManager.RESULT_GOTO_LEVEL, intent);
        activity.finish();
    }

    public void setShowProgressIndicator(boolean value) {
        // TODO implement a loading progress indicator again, maybe
    }

    protected void showActionMenu(Item item, View actionButton) {
        if (item instanceof StandardMenuItem) {
            StandardMenuItem menuItem = (StandardMenuItem) item;
            menuItem.showContextMenu(this, actionButton);
        }
    }

    protected boolean hijackCommand(String title, List<String> commands, List<String> params, @Nullable String nextWindow, boolean fromContextMenu) {
        if (CommandTools.commandMatches(commands, "jivefavorites", "add")) {
            executeRawMenuCommand(title, Arrays.asList("favorites", "add"), params, NextWindowNames.NOREFRESH, true, null);
            return true;
        }
        if (CommandTools.commandMatches(commands, "jivefavorites", "delete")) {

            CharSequence message = HtmlCompat.fromHtml(getString(R.string.removefavorite_action_confirmation_html, title), 0);

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setTitle(R.string.confirmation_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        // TODO only refresh if we're in the favorites hierarchy
                        executeRawMenuCommand(title, Arrays.asList("favorites", "delete"), params,
                                NextWindowNames.REFRESH, true, null);
                    })
                    .setNegativeButton(R.string.cancel, (dialog, which) -> {

                    })
                    .show();
            return true;
        }
        if (CommandTools.commandAsString(commands).startsWith("artwork ")) {
            // this is a "do" command, but we do it as a "go" with a menu
            NavigationCommandSet requestCommandSet = new NavigationCommandSet(commands, params);
            executeMenuBrowse(title, requestCommandSet, null, null, null);
            return true;
        }
        return false;
    }
}
