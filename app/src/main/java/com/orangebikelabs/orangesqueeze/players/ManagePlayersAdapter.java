/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.content.Context;

import arrow.core.Option;

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.MoreMath;
import com.orangebikelabs.orangesqueeze.common.MoreOption;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OtherPlayerInfo;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.StringTools;
import com.orangebikelabs.orangesqueeze.common.SyncStatus;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class ManagePlayersAdapter extends ArrayAdapter<AbsPlayerItem> {
    public enum PlayerCommand {
        POWER, VOLUME
    }

    private enum ViewType {
        PLAYERSTATUS, SP_LAUNCH_MARKET, SP_START_SERVICE, SEPARATOR, OTHER_PLAYER, SN_PLAYER, MORE_PLAYERS
    }

    public interface OnPlayerCommandListener {
        void onPlayerCommand(PlayerId playerId, PlayerCommand command, @Nullable Object extra);

        void onActionButton(View clickView, AbsPlayerItem item);
    }

    final private Multimap<PlayerId, FutureResult> mPlayerWorkTokenMap = LinkedHashMultimap.create();

    // TODO find a better way to handle this than weak hash map
    final private Set<View> mViewSet = Collections.newSetFromMap(new WeakHashMap<>());

    @Nullable
    private PlayerId mSelectedPlayer;
    private boolean mInVolumeDrag;
    private boolean mLaunchingSqueezePlayer;
    private OnPlayerCommandListener mOnPlayerCommandListener;
    private SyncStatus mSyncStatus;
    private boolean mDrawerMode;
    private int mPlayerItemLayoutRid;
    final private NumberFormat mNumberFormat;

    public ManagePlayersAdapter(Context context) {
        super(context, 0);

        setDrawerMode(false);
        mNumberFormat = NumberFormat.getInstance();
    }

    @Nonnull
    @Override
    public AbsPlayerItem getItem(int position) {
        return super.getItem(position);
    }

    final public void setDrawerMode(boolean drawerMode) {
        mDrawerMode = drawerMode;
        if (mDrawerMode) {
            mPlayerItemLayoutRid = R.layout.manageplayers_draweritem;
        } else {
            mPlayerItemLayoutRid = R.layout.manageplayers_item;
        }
    }

    public void setFromLoader(PlayerListLoader.PlayerListContainer info) {
        setNotifyOnChange(false);
        clear();

        mSelectedPlayer = info.mCurrentPlayerId;
        mSyncStatus = info.mPlayerSyncState;

        if (mLaunchingSqueezePlayer && info.mSqueezePlayerFound) {
            mLaunchingSqueezePlayer = false;
        }

        List<PlayerStatus> groups = new ArrayList<>();

        SortedSet<PlayerStatus> sorted = new TreeSet<>(new PlayerListComparator());
        sorted.addAll(info.mConnectedPlayers.values());
        int lastSyncGroup = -1;
        for (PlayerStatus s : sorted) {
            if ("group".equals(s.getModel())) {
                groups.add(s);
                continue;
            }
            int syncGroup = MoreOption.getOrElse(mSyncStatus.getSyncGroup(s.getId()), 0);
            if (lastSyncGroup != syncGroup) {
                if (syncGroup == 0) {
                    add(new SeparatorItem(getContext().getString(R.string.unsynchronized_player_separator)));
                } else {
                    add(new SeparatorItem(getContext().getString(R.string.synchronized_player_separator, syncGroup)));
                }
                lastSyncGroup = syncGroup;
            }
            add(new PlayerItem(s));
        }
        if (!groups.isEmpty()) {
            add(new SeparatorItem(getContext().getString(R.string.group_players_separator)));
            for (PlayerStatus s : groups) {
                add(new PlayerItem(s));
            }
        }

        if (!mDrawerMode) {
            if (info.mSqueezePlayerAvailable) {
                if (!info.mSqueezePlayerFound) {
                    add(new StartSqueezePlayerService());
                }
            }
        }

        boolean otherServerHeader = false;
        for (OtherPlayerInfo opi : info.mOtherPlayers) {
            if (info.mConnectedPlayers.containsKey(opi.getId())) {
                // skip "other" players that exist in our player list
                continue;
            }
            if (!otherServerHeader) {
                otherServerHeader = true;
                add(new SeparatorItem(getContext().getString(R.string.other_servers_player_separator)));
            }
            add(new OtherPlayer(opi));
        }

        notifyDataSetChanged();
    }

    public void setOnPlayerCommandListener(OnPlayerCommandListener l) {
        mOnPlayerCommandListener = l;
    }

    public void addPlayerResult(PlayerId playerId, FutureResult futureResult) {
        mPlayerWorkTokenMap.put(playerId, futureResult);
        refreshPlayer(playerId, null);
    }

    protected boolean isWorkInProgress(PlayerId playerId) {
        return !mPlayerWorkTokenMap.get(playerId).isEmpty();
    }

    public void setLaunchingSqueezePlayer(boolean launching) {
        mLaunchingSqueezePlayer = launching;
    }

    public boolean getLaunchingSqueezePlayer() {
        return mLaunchingSqueezePlayer;
    }

    /**
     * add updated player status to the mix
     */
    public void addPlayerStatus(PlayerStatus status) {
        // clean up any committed results

        // create copy of player id list, since this will change if we remove a player (and throw concurrent mod exception)
        ArrayList<PlayerId> copy = new ArrayList<>(mPlayerWorkTokenMap.keySet());
        for (PlayerId pid : copy) {
            Iterator<FutureResult> it = mPlayerWorkTokenMap.get(pid).iterator();
            while (it.hasNext()) {
                FutureResult result = it.next();
                if (result.isCommitted()) {
                    it.remove();
                }
            }
        }
        refreshPlayer(status.getId(), status);
    }

    /**
     * force refresh of a specific player id only
     */
    protected void refreshPlayer(PlayerId playerId, @Nullable PlayerStatus newPlayerStatus) {
        // iterate through each item
        for (int i = 0; i < getCount(); i++) {
            AbsPlayerItem item = getItem(i);
            if (!(item instanceof PlayerItem)) {
                continue;
            }

            PlayerItem pi = (PlayerItem) item;
            if (!pi.getPlayerId().equals(playerId)) {
                continue;
            }


            // update player status for the item
            if (newPlayerStatus != null) {
                pi.setPlayerStatus(newPlayerStatus);
            }

            // update all the views associated with this PlayerId
            for (View v : mViewSet) {
                PlayerId pid = (PlayerId) v.getTag(R.id.tag_playerid);
                if (playerId.equals(pid)) {
                    pi.updateView(v);
                }
            }
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        AbsPlayerItem item = getItem(position);
        return !(item instanceof SeparatorItem);
    }

    public boolean isInVolumeDrag() {
        return mInVolumeDrag;
    }

    public void setSelectedPlayer(PlayerId playerId) {
        mSelectedPlayer = playerId;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        AbsPlayerItem item = getItem(position);
        return item.getViewType();
    }

    @Override
    public int getViewTypeCount() {
        return ViewType.values().length;
    }

    @Override
    @Nonnull
    public View getView(int pos, @Nullable View convertView, ViewGroup parent) {

        AbsPlayerItem item = getItem(pos);
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(item.getLayoutRid(), parent, false);
            mViewSet.add(convertView);
            OSAssert.assertNotNull(convertView, "inflated view shouldn't be null");
        }

        item.initView(convertView);
        item.updateView(convertView);

        return convertView;
    }

    /** called when volume is set by user */
    protected void triggerVolumeChangeFromUser(Slider sb, int volume) {
        View parentView = (View) sb.getTag(R.id.tag_containerview);
        PlayerId pid = (PlayerId) parentView.getTag(R.id.tag_playerid);
        mOnPlayerCommandListener.onPlayerCommand(pid, PlayerCommand.VOLUME, volume);
    }

    protected void hideVolumeText(@Nullable TextView playerVolumeLabel) {
        if(playerVolumeLabel != null) {
            playerVolumeLabel.setVisibility(View.INVISIBLE);
        }
    }

    /** called to update the text field to a specific volume level */
    protected void showVolumeText(@Nullable TextView playerVolumeLabel, int volume) {
        if(playerVolumeLabel != null && !mInVolumeDrag) {
            if(playerVolumeLabel.getVisibility() != View.VISIBLE) {
                playerVolumeLabel.setVisibility(View.VISIBLE);
            }
            playerVolumeLabel.setText(mNumberFormat.format(volume));
        }
    }

    /**
     * tag should be the player status
     */
    final private OnClickListener mPowerButtonClicked = new OnClickListener() {

        @Override
        public void onClick(View v) {
            SwitchMaterial powerButton = (SwitchMaterial) v;
            View parentView = (View) v.getTag(R.id.tag_containerview);
            PlayerId pid = (PlayerId) parentView.getTag(R.id.tag_playerid);
            mOnPlayerCommandListener.onPlayerCommand(pid, PlayerCommand.POWER, powerButton.isChecked());
        }
    };

    /**
     * tag should be the adapter item
     */
    final private OnClickListener mActionButtonClicked = new OnClickListener() {

        @Override
        public void onClick(View v) {
            AbsPlayerItem item = (AbsPlayerItem) v.getTag(R.id.tag_item);
            mOnPlayerCommandListener.onActionButton(v, item);
        }
    };

    static public class SeparatorItem extends AbsPlayerItem {
        @Nonnull
        final private String mTitle;

        SeparatorItem(String title) {
            mTitle = title;
        }

        @Override
        protected int getViewType() {
            return ViewType.SEPARATOR.ordinal();
        }

        @Override
        protected int getLayoutRid() {
            return R.layout.manageplayers_separator;
        }

        @Override
        protected void updateView(View view) {
            TextView text = view.findViewById(R.id.text1);
            text.setText(mTitle);
        }

        @Override
        protected String getName() {
            return null;
        }
    }

    public class PlayerItem extends AbsPlayerItem {
        @Nonnull
        final private PlayerId mPlayerId;

        @Nonnull
        private PlayerStatus mPlayerStatus;

        PlayerItem(PlayerStatus playerStatus) {
            mPlayerId = playerStatus.getId();
            mPlayerStatus = playerStatus;
        }

        @Override
        protected void initView(View view) {
            super.initView(view);

            view.setTag(R.id.tag_playerid, mPlayerId);
        }

        @Nonnull
        public PlayerId getPlayerId() {
            return mPlayerId;
        }

        @Nonnull
        public PlayerStatus getPlayerStatus() {
            return mPlayerStatus;
        }

        public void setPlayerStatus(PlayerStatus newStatus) {
            mPlayerStatus = newStatus;
        }

        @Nonnull
        public Option<Integer> getSyncGroup() {
            return mSyncStatus.getSyncGroup(mPlayerStatus.getId());
        }

        @Override
        public void onClick(SBContext context, ManagePlayersFragment fragment) {
            setSelectedPlayer(mPlayerStatus.getId());
            context.setPlayerById(mPlayerStatus.getId());
        }

        @Override
        protected int getViewType() {
            return ViewType.PLAYERSTATUS.ordinal();
        }

        @Override
        protected int getLayoutRid() {
            return mPlayerItemLayoutRid;
        }

        @Nonnull
        @Override
        protected String getName() {
            return mPlayerStatus.getName();
        }

        @Override
        protected void updateView(View view) {
            TextView playerNameText = view.findViewById(R.id.player_name_label);
            SwitchMaterial powerButton = view.findViewById(R.id.player_power_toggle);
            Slider volumeBar = view.findViewById(R.id.volume_bar);
            View actionButton = view.findViewById(R.id.action_button);
            TextView playerStatusLabel = view.findViewById(R.id.player_status_label);
            final TextView playerVolumeLabel = view.findViewById(R.id.player_volume_label);

            if (powerButton != null) {
                powerButton.setTag(R.id.tag_containerview, view);
                powerButton.setOnClickListener(mPowerButtonClicked);
            }

            if (volumeBar != null) {
                OSAssert.assertNotNull(playerVolumeLabel, "should never be null if volumebar is non-null");

                volumeBar.setTickVisible(false);
                volumeBar.setStepSize(1.0f);
                volumeBar.setValueFrom(0);
                volumeBar.setValueTo(100);
                volumeBar.setTag(R.id.tag_containerview, view);
                volumeBar.addOnChangeListener((slider, value, fromUser) -> {
                    int volume = (int)value;
                    showVolumeText(playerVolumeLabel, volume);
                    if (fromUser) {
                        // send volume change to server
                        triggerVolumeChangeFromUser(slider, volume);
                    }
                });
                volumeBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
                    @Override
                    public void onStartTrackingTouch(Slider slider) {
                        mInVolumeDrag = true;
                        hideVolumeText(playerVolumeLabel);
                    }

                    @Override
                    public void onStopTrackingTouch(Slider slider) {
                        mInVolumeDrag = false;
                        // called at the end of a touch/drag cycle, call with update to make
                        // sure we are in sync with remote volume
                        int volume = (int)slider.getValue();
                        showVolumeText(playerVolumeLabel, volume);
                    }
                });
                volumeBar.setLabelFormatter((value -> Integer.toString((int) value)));
            }

            if (actionButton != null) {
                actionButton.setTag(R.id.tag_item, this);
                actionButton.setOnClickListener(mActionButtonClicked);
            }

            ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

            playerNameText.setText(mPlayerStatus.getName());

            // when progress is visible, don't routinely update the volume,
            // power buttons because they may lag

            int clampedVolume = MoreMath.coerceIn(mPlayerStatus.getVolume(), 0, 100);
            int playerStatusVisibility = View.GONE;
            boolean statusInProgress = false;
            if (!mPlayerStatus.isInitialized()) {
                statusInProgress = true;

                if (volumeBar != null) {
                    volumeBar.setValue(0);
                    volumeBar.setEnabled(false);
                }

                if (powerButton != null) {
                    powerButton.setChecked(false);
                }
            } else if (isWorkInProgress(mPlayerStatus.getId())) {
                statusInProgress = true;
            } else {
                if (powerButton != null) {
                    powerButton.setChecked(mPlayerStatus.isPowered());
                }
                if (mPlayerStatus.isMuted()) {
                    // TODO show different info for muted player
                    if (volumeBar != null) {
                        volumeBar.setValue(0);
                    }
                } else {
                    if (volumeBar != null) {
                        // ensure received values are within 0 <= value <= 100
                        volumeBar.setValue(clampedVolume);
                    }
                }
                showVolumeText(playerVolumeLabel, clampedVolume);

                // TODO add different text if mPlayerStatus.isVolumeLocked()
                if (volumeBar != null) {
                    volumeBar.setEnabled(true);
                }
                if (mPlayerStatus.isPowered()) {
                    playerStatusVisibility = View.VISIBLE;
                    if (playerStatusLabel != null) {
                        if (mPlayerStatus.isConnected()) {
                            playerStatusLabel.setText(mPlayerStatus.getMode().getRid());
                        } else {
                            playerStatusLabel.setText(getContext().getString(R.string.disconnected));
                        }
                    }
                }
            }

            if(statusInProgress) {
                hideVolumeText(playerVolumeLabel);
            } else {
                showVolumeText(playerVolumeLabel, clampedVolume);
            }
            if (playerStatusLabel != null) {
                playerStatusLabel.setVisibility(playerStatusVisibility);
            }
            if (mPlayerStatus.getId().equals(mSelectedPlayer)) {
                view.setBackgroundResource(R.drawable.manageplayers_item_selected);
            } else {
                view.setBackgroundResource(0);
            }
        }
    }

    static public class OtherPlayer extends AbsPlayerItem {
        final protected OtherPlayerInfo mInfo;

        public OtherPlayer(OtherPlayerInfo info) {
            mInfo = info;
        }

        @Nonnull
        public OtherPlayerInfo getInfo() {
            return mInfo;
        }

        @Override
        protected int getViewType() {
            return ViewType.OTHER_PLAYER.ordinal();
        }

        @Override
        protected int getLayoutRid() {
            return R.layout.manageplayers_otherplayer;
        }

        @Override
        protected void updateView(View view) {
            TextView playerName = view.findViewById(R.id.text1);
            playerName.setText(mInfo.getName());

            TextView serverName = view.findViewById(R.id.text2);
            serverName.setText(mInfo.getServerName());
        }

        @Override
        protected String getName() {
            return mInfo.getName();
        }

        @Override
        public void onClick(SBContext context, ManagePlayersFragment fragment) {
            try {
                String serverUrl = mInfo.getServerUrl();
                if (serverUrl == null) {
                    Reporting.report(null, "null server uri", mInfo);
                    return;
                }
                final URI fServerUrl = new URI(serverUrl);
                String message = fragment.getString(R.string.player_connect_server_confirmation_message, mInfo.getName(), context.getConnectionInfo().getServerName());

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(fragment.requireActivity());
                builder
                        .setTitle(R.string.player_connect_server_confirmation_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.ok, (dialog, which) -> {
                            fragment.control_performOtherPlayerConnect(mInfo.getId(), fServerUrl.getHost());
                        })
                        .setNegativeButton(R.string.cancel, (dialog, which) -> {
                        })
                        .show();
            } catch (URISyntaxException e) {
                Reporting.report(e);
            }
        }
    }

    public class StartSqueezePlayerService extends AbsPlayerItem {
        @Override
        protected int getViewType() {
            return ViewType.SP_START_SERVICE.ordinal();
        }

        @Override
        protected int getLayoutRid() {
            return R.layout.manageplayers_launchsp_item;
        }

        @Override
        protected void updateView(View view) {
            ProgressBar progressBar = view.findViewById(R.id.progress);
            progressBar.setVisibility(mLaunchingSqueezePlayer ? View.VISIBLE : View.INVISIBLE);
        }

        @Override
        protected String getName() {
            return "SqueezePlayer";
        }

        @Override
        public void onClick(SBContext context, ManagePlayersFragment fragment) {
            setLaunchingSqueezePlayer(true);

            SqueezePlayerHelper.startService(fragment.requireContext());
            context.setAutoSelectSqueezePlayer(true);
        }
    }

    // sort first by sync group, then by name
    private class PlayerListComparator implements Comparator<PlayerStatus> {
        protected PlayerListComparator() {
        }

        @Override
        public int compare(PlayerStatus lhs, PlayerStatus rhs) {
            int lhsSyncGroup = MoreOption.getOrElse(mSyncStatus.getSyncGroup(lhs.getId()), 0);

            int rhsSyncGroup = MoreOption.getOrElse(mSyncStatus.getSyncGroup(rhs.getId()), 0);
            return ComparisonChain.start().
                    // all players in sync group first
                            compareTrueFirst(lhsSyncGroup != 0, rhsSyncGroup != 0).


                    // then order by sync group, ascending
                            compare(lhsSyncGroup, rhsSyncGroup).

                    // sort by player name
                            compare(lhs.getName(), rhs.getName(), StringTools.getNullSafeCollator()).

                    // sort by player id
                            compare(lhs.getId(), rhs.getId(), Ordering.usingToString()).

                    result();
        }
    }
}
