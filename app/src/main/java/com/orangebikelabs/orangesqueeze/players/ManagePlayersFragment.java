/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.Loader;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Joiner;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.actions.ActionDialogBuilder;
import com.orangebikelabs.orangesqueeze.app.SBFragment;
import com.orangebikelabs.orangesqueeze.app.SimpleResult;
import com.orangebikelabs.orangesqueeze.browse.AbsBrowseFragment;
import com.orangebikelabs.orangesqueeze.browse.BrowseStyle;
import com.orangebikelabs.orangesqueeze.browse.node.BrowseNodeFragment;
import com.orangebikelabs.orangesqueeze.common.DelayedFuture;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.MenuTools;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerNotFoundException;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.SBRequest;
import com.orangebikelabs.orangesqueeze.common.SBRequest.Type;
import com.orangebikelabs.orangesqueeze.common.SBResult;
import com.orangebikelabs.orangesqueeze.common.SyncStatus;
import com.orangebikelabs.orangesqueeze.common.event.AnyPlayerStatusEvent;
import com.orangebikelabs.orangesqueeze.players.ManagePlayersAdapter.OnPlayerCommandListener;
import com.orangebikelabs.orangesqueeze.players.ManagePlayersAdapter.PlayerCommand;
import com.orangebikelabs.orangesqueeze.players.PlayerListLoader.PlayerListContainer;
import com.orangebikelabs.orangesqueeze.ui.VolumeFragment;
import com.squareup.otto.Subscribe;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
public class ManagePlayersFragment extends SBFragment implements LoaderCallbacks<PlayerListLoader.PlayerListContainer> {
    final private static int PLAYER_LIST_LOADER = 0;

    @Nullable
    protected FutureResult mLastVolumeRequest;

    @Nullable
    protected FutureResult mLastSyncRequest;

    protected ListView mListView;
    protected ManagePlayersAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.manageplayers, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBus.register(mEventReceiver);

    }

    /** subclass can override this to remove behavior */
    protected void setupMenuProvider() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                // use activity menu
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                return false;
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuTools.setVisible(menu, R.id.menu_players, false);
            }
        }, this, Lifecycle.State.RESUMED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBus.unregister(mEventReceiver);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mListView = view.findViewById(android.R.id.list);

        mListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        mListView.setItemsCanFocus(true);
        mListView.setOnItemClickListener((parent, v, position, id) -> onListItemClick(v, position, id));

        mAdapter = new ManagePlayersAdapter(requireContext());
        mAdapter.setOnPlayerCommandListener(mPlayerCommandListener);
        mListView.setAdapter(mAdapter);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        LoaderManager.getInstance(this).initLoader(PLAYER_LIST_LOADER, null, this);
    }

    @Override
    @Nonnull
    public Loader<PlayerListContainer> onCreateLoader(int loaderId, @Nullable Bundle args) {
        switch (loaderId) {
            case PLAYER_LIST_LOADER:
                return new PlayerListLoader(requireContext());
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void onLoadFinished(Loader<PlayerListContainer> loader, PlayerListLoader.PlayerListContainer info) {
        switch (loader.getId()) {
            case PLAYER_LIST_LOADER:
                if (!mAdapter.isInVolumeDrag() && (mLastSyncRequest == null || mLastSyncRequest.isCommitted())) {
                    mLastSyncRequest = null;

                    mAdapter.setFromLoader(info);
                }
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void onLoaderReset(Loader<PlayerListContainer> loader) {
        // nothing
    }

    protected void onListItemClick(View v, int position, long id) {
        AbsPlayerItem item = mAdapter.getItem(position);
        item.onClick(mSbContext, this);
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenAnyPlayerStatusChanges(AnyPlayerStatusEvent event) {
            if (!mAdapter.isInVolumeDrag()) {
                if (mLastVolumeRequest == null || mLastVolumeRequest.isCommitted()) {
                    mLastVolumeRequest = null;
                    mAdapter.addPlayerStatus(event.getPlayerStatus());
                }
            }
        }
    };

    final private OnPlayerCommandListener mPlayerCommandListener = new OnPlayerCommandListener() {

        @Override
        public void onPlayerCommand(PlayerId playerId, PlayerCommand command, @Nullable Object extra) {
            FutureResult futureResult = null;

            switch (command) {
                case POWER:
                    OSAssert.assertNotNull(extra, "not null");
                    boolean powered = (Boolean) extra;
                    futureResult = mSbContext.sendPlayerCommand(playerId, Arrays.asList("power", powered ? 1 : 0));
                    break;
                case VOLUME:
                    // for now, don't save token because it will take too long to
                    // commit
                    OSAssert.assertNotNull(extra, "not null");
                    mLastVolumeRequest = mSbContext.setPlayerVolume(playerId, ((Integer) extra));
                    break;
            }
            if (futureResult != null) {
                mAdapter.addPlayerResult(playerId, futureResult);
            }
        }

        @Override
        public void onActionButton(View view, AbsPlayerItem item) {
            ActionDialogBuilder<AbsPlayerItem> builder = ActionDialogBuilder.newInstance(ManagePlayersFragment.this, view);
            builder.setAvailableActions(AbsPlayerAction.getContextActionCandidates(requireContext()));

            if (builder.applies(item)) {
                String title = getString(R.string.actionmenu_title_html, item.getName());
                builder.setTitle(HtmlCompat.fromHtml(title, 0));
                builder.create().show();
            }
        }
    };

    static class SyncChoice implements Serializable {

        final ArrayList<PlayerId> mIds = new ArrayList<>();
        final String mText;

        SyncChoice(Context context, PlayerStatus[] players) {
            if (players.length == 0) {
                mText = context.getString(R.string.players_none);
            } else {
                ArrayList<String> playerNames = new ArrayList<>();
                for (PlayerStatus s : players) {
                    playerNames.add(s.getName());
                    mIds.add(s.getId());
                }
                mText = Joiner.on(", ").join(playerNames);
            }
        }

        @Override
        @Nonnull
        public String toString() {
            return mText;
        }
    }

    public void control_volume(PlayerId playerId) {
        if (!isAdded()) {
            return;
        }

        VolumeFragment fragment = VolumeFragment.newInstance(playerId, false);
        fragment.show(getParentFragmentManager(), "volume");
    }

    public void control_stopSqueezePlayer(PlayerId playerId) {
        if (!isAdded()) {
            return;
        }

        // this will be discarded with the PlayerItem
        SBResult result = new SimpleResult(MissingNode.getInstance());
        FutureResult futureResult = FutureResult.result(new DelayedFuture<>(result, 10, TimeUnit.SECONDS));
        mAdapter.addPlayerResult(playerId, futureResult);
        SqueezePlayerHelper.stopService(requireContext());
    }

    public void control_renamePlayer(PlayerId id) {
        if (!isAdded()) {
            return;
        }

        try {
            PlayerStatus opStatus = mSbContext.getServerStatus().getCheckedPlayerStatus(id);
            RenamePlayerDialog.newInstance(this, opStatus).show();
        } catch (PlayerNotFoundException e) {
            OSLog.i(e.getMessage(), e);
        }
    }

    public void control_performPlayerRename(PlayerId playerId, String newName) {
        if (!isAdded()) {
            return;
        }

        try {
            PlayerStatus current = mSbContext.getServerStatus().getCheckedPlayerStatus(playerId);

            newName = newName.trim();

            if (current.getName().equals(newName)) {
                // same name!
                return;
            }

            if (newName.length() == 0) {
                // show error, no blanks

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                builder.setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.renameplayer_empty_title)
                        .setMessage(R.string.renameplayer_empty_message)
                        .show();

                return;
            }

            for (PlayerStatus s : mSbContext.getServerStatus().getAvailablePlayers()) {
                if (s.getName().equals(newName)) {
                    // show error, duplicate player name

                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
                    builder.setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.renameplayer_duplicate_title)
                            .setMessage(R.string.renameplayer_duplicate_message)
                            .show();

                    return;
                }
            }

            FutureResult result = mSbContext.renamePlayer(playerId, newName);
            mAdapter.addPlayerResult(playerId, result);
        } catch (PlayerNotFoundException e) {
            OSLog.i(e.getMessage(), e);
        }
    }

    public void control_synchronization(PlayerId playerId) {
        if (!isAdded()) {
            return;
        }

        try {
            PlayerStatus opStatus = mSbContext.getServerStatus().getCheckedPlayerStatus(playerId);

            ArrayList<SyncChoice> choices = new ArrayList<>();
            SyncChoice selectedChoice = null;

            boolean noneSelected = false;
            // sort this map on the key
            SyncStatus syncStatus = mSbContext.getServerStatus().getSyncStatus().getReadonlySnapshot();

            Map<Integer, ArrayList<PlayerStatus>> groups = new TreeMap<>();
            for (PlayerStatus s : mSbContext.getServerStatus().getAvailablePlayers()) {
                Integer groupNumber = syncStatus.getSyncGroup(s.getId()).orElse(null);
                if (groupNumber == null) {
                    // don't add current player
                    if (s == opStatus) {
                        noneSelected = true;
                    } else {
                        choices.add(new SyncChoice(requireContext(), new PlayerStatus[]{s}));
                    }
                } else {
                    // start building list of groups
                    ArrayList<PlayerStatus> list = groups.get(groupNumber);
                    if (list == null) {
                        list = new ArrayList<>();
                        groups.put(groupNumber, list);
                    }

                    // leave out the current player from group
                    if (s != opStatus) {
                        list.add(s);
                    }
                }
            }

            // so far, we've added the single player items to the list, now insert
            // the groups up top
            int insertIndex = 0;
            for (Map.Entry<Integer, ArrayList<PlayerStatus>> entry : groups.entrySet()) {
                int group = entry.getKey();
                ArrayList<PlayerStatus> list = entry.getValue();

                SyncChoice newChoice = new SyncChoice(requireContext(), list.toArray(new PlayerStatus[0]));
                Integer opGroup = syncStatus.getSyncGroup(opStatus.getId()).orElse(null);
                if (opGroup != null && opGroup == group) {
                    selectedChoice = newChoice;
                }
                choices.add(insertIndex++, newChoice);
            }

            // finally, add the "desync" option which may or may not be selected
            // already
            SyncChoice newChoice = new SyncChoice(requireContext(), new PlayerStatus[0]);
            if (noneSelected) {
                selectedChoice = newChoice;
            }
            choices.add(newChoice);

            CharSequence[] adaptedChoices = new CharSequence[choices.size()];
            for (int i = 0; i < adaptedChoices.length; i++) {
                adaptedChoices[i] = choices.get(i).mText;
            }

            // launch the dialog
            int selectedIndex = choices.indexOf(selectedChoice);
            String title = getString(R.string.synchronization_title_html, opStatus.getName());
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setTitle(HtmlCompat.fromHtml(title, 0))
                    .setItems(adaptedChoices, (dlg, ndx) -> {

                        SyncChoice syncChoice = choices.get(ndx);

                        if (syncChoice.mIds.isEmpty()) {
                            mLastSyncRequest = mSbContext.unsynchronize(playerId);
                        } else {
                            PlayerId targetId = syncChoice.mIds.get(0);
                            mLastSyncRequest = mSbContext.synchronize(playerId, targetId);
                        }
                    })
                    .show();
        } catch (PlayerNotFoundException e) {
            OSLog.i(e.getMessage(), e);
        }
    }

    public void control_unsync(PlayerId playerId) {
        if (!isAdded()) {
            return;
        }

        mLastSyncRequest = mSbContext.unsynchronize(playerId);
    }

    public void control_performOtherPlayerConnect(PlayerId playerId, String serverHost) {
        if (!isAdded()) {
            return;
        }

        SBRequest request = mSbContext.newRequest(Type.COMET, "disconnect", playerId.toString(), serverHost);
        request.submit(OSExecutors.getUnboundedPool());
    }

    public void control_showSleepPlayerDialog(PlayerId id) {
        if (!isAdded()) {
            return;
        }

        try {
            PlayerStatus status = mSbContext.getServerStatus().getCheckedPlayerStatus(id);
            MaterialDialog dlg = PlayerSleepDialog.create(this, status.getId(), (playerId, sleepTime, sleepUnit) -> {
                long seconds = sleepUnit.toSeconds(sleepTime);
                if (seconds == 0) {
                    mSbContext.sendPlayerCommand(playerId, "jiveendoftracksleep");
                } else {
                    mSbContext.sendPlayerCommand(playerId, "sleep", Long.toString(seconds));
                }
                SBPreferences.get().setLastPlayerSleepTime(sleepTime, sleepUnit);
            });
            dlg.show();
        } catch (PlayerNotFoundException e) {
            // ignore
        }
    }

    public void control_browseMoreMenu(PlayerId playerId) {
        if (!isAdded()) {
            return;
        }

        try {
            PlayerStatus status = mSbContext.getServerStatus().getCheckedPlayerStatus(playerId);

            String title = getString(R.string.player_settings_title, status.getName());
            Intent settingsIntent = getNavigationManager().newBrowseNodeIntent(title, "settings", status.getId());
            settingsIntent.putExtra(BrowseNodeFragment.PARAM_BROWSE_STYLE, (Parcelable) BrowseStyle.LIST);
            settingsIntent.putExtra(AbsBrowseFragment.PARAM_ALWAYS_REFRESH_ON_RESTART, true);
            getNavigationManager().startActivity(settingsIntent);
        } catch (PlayerNotFoundException e) {
            // ignore
        }
    }
}
