/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.Keep;

import android.util.SparseArray;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.artwork.Artwork;
import com.orangebikelabs.orangesqueeze.players.SqueezePlayerHelper;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

@Immutable
@ThreadSafe
@Keep
public class PlayerStatus {
    final static private ImmutableSet<String> sSqueezenetworkIncapableModels = ImmutableSet.of("slimp3", "squeezebox");

    public enum Mode {
        STOPPED("stop", R.string.playmode_stopped),
        PLAYING("play", R.string.playmode_playing),
        PAUSED("pause", R.string.playmode_paused);

        final private String mCommand;
        final private int mRid;

        Mode(String command, int rid) {
            mCommand = command;
            mRid = rid;
        }

        public int getRid() {
            return mRid;
        }

        public String getCommand() {
            return mCommand;
        }

        @Nullable
        static public Mode fromCommand(String str) {
            for (Mode m : values()) {
                if (m.mCommand.equals(str)) {
                    return m;
                }
            }
            return null;
        }
    }

    public enum RepeatMode {
        OFF, TRACK, PLAYLIST
    }

    public enum ShuffleMode {
        OFF, TRACK, ALBUM
    }

    public enum PlayerButton {
        BACK("rew"), FORWARD("fwd"), SHUFFLE("shuffle"), REPEAT("repeat"), THUMBSUP("repeat"), THUMBSDOWN("shuffle");

        final private String mKey;

        PlayerButton(String key) {
            mKey = key;
        }

        public String getKey() {
            return mKey;
        }

        static public PlayerButton fromKey(String key) {
            for (PlayerButton p : values()) {
                if (p.mKey.equals(key)) {
                    return p;
                }
            }
            return null;
        }
    }

    private enum Attributes {
        // @formatter:off
        ID, NAME, ARTIST, TRACKARTIST, ALBUM, TRACK, ALBUMID, ARTISTID, MODE,
        YEAR, TRACKNUMBER, ELAPSEDTIME, TOTALTIME, PLAYLIST_TIMESTAMP, ARTWORK,
        PLAYLIST_INDEX, PLAYLIST_TRACKCOUNT, IS_SEEKABLE, IS_VOLUME_LOCKED,
        IS_THUMBSUP_PRESSED, IS_THUMBSDOWN_PRESSED, VOLUME, IS_POWERED, ADDRESS,
        IS_LOCAL_SQUEEZEPLAYER, IS_CONNECTED, REPEATMODE, SHUFFLEMODE, TIMEBASIS, MODEL,
        TRACKHASH, BUTTONSTATUS_MAP, TRACKID, TRACKINFO, REMOTE, CURRENT_TITLE
        // @formatter:on
    }

    @Nonnull
    final private SparseArray<Object> mAttributes;

    final private boolean mInitialized;

    @Nonnull
    final private AtomicBoolean mMenuLoadTriggered = new AtomicBoolean(false);

    /**
     * construct an empty playerstatus
     */
    public PlayerStatus(PlayerId playerId) {
        mAttributes = new SparseArray<>();
        put(mAttributes, Attributes.ID, playerId);
        mInitialized = false;
    }

    /**
     * create a modified playerstatus
     */
    protected PlayerStatus(PlayerStatus basis, SparseArray<Object> override) {
        mAttributes = new SparseArray<>(basis.mAttributes.size() + override.size());
        SparseArrayTools.copy(mAttributes, basis.mAttributes);
        SparseArrayTools.copy(mAttributes, override);
        mInitialized = true;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public boolean isVolumeLocked() {
        return get(Attributes.IS_VOLUME_LOCKED, false);
    }

    public boolean isThumbsUpPressed() {
        return get(Attributes.IS_THUMBSUP_PRESSED, false);
    }

    public boolean isThumbsUpEnabled() {
        ButtonStatus status = getButtonStatus(PlayerButton.THUMBSUP).orNull();
        return status != null && status.isThumbsUp();
    }

    public boolean isThumbsDownPressed() {
        return get(Attributes.IS_THUMBSDOWN_PRESSED, false);
    }

    public boolean isThumbsDownEnabled() {
        ButtonStatus status = getButtonStatus(PlayerButton.THUMBSDOWN).orNull();
        return status != null && status.isThumbsDown();
    }

    @Nonnull
    public RepeatMode getRepeatMode() {
        return get(Attributes.REPEATMODE, RepeatMode.OFF);
    }

    @Nonnull
    public ShuffleMode getShuffleMode() {
        return get(Attributes.SHUFFLEMODE, ShuffleMode.OFF);
    }

    public boolean isRemote() {
        return get(Attributes.REMOTE, false);
    }

    @Nullable
    public String getCurrentTitle() {
        return get(Attributes.CURRENT_TITLE, null);
    }

    @Nonnull
    public Optional<ButtonStatus> getButtonStatus(PlayerButton button) {
        Map<PlayerButton, ButtonStatus> status = get(Attributes.BUTTONSTATUS_MAP, Collections.emptyMap());
        return Optional.fromNullable(status.get(button));
    }

    @Nonnull
    public PlayerId getId() {
        return (PlayerId) mAttributes.get(Attributes.ID.ordinal());
    }

    @Nonnull
    public String getName() {
        return get(Attributes.NAME, "");
    }

    @Nonnull
    public String getArtist() {
        return get(Attributes.ARTIST, "");
    }

    @Nonnull
    public String getTrackArtist() {
        return get(Attributes.TRACKARTIST, "");
    }

    /**
     * use track artist if available, otherwise use artist
     */
    @Nonnull
    public String getDisplayArtist() {
        String retval = getTrackArtist();
        if (retval.length() == 0) {
            retval = getArtist();
        }
        return retval;
    }

    @Nonnull
    public Optional<String> getArtistId() {
        return Optional.fromNullable(get(Attributes.ARTISTID, null));
    }

    @Nonnull
    public Optional<String> getAlbumId() {
        return Optional.fromNullable(get(Attributes.ALBUMID, null));
    }

    @Nonnull
    public String getAlbum() {
        return get(Attributes.ALBUM, "");
    }

    @Nonnull
    public String getTrack() {
        return get(Attributes.TRACK, "");
    }

    @Nonnull
    public Optional<String> getTrackId() {
        return Optional.fromNullable(get(Attributes.TRACKID, null));
    }

    @Nonnull
    public Optional<String> getYear() {
        String year = (String) get(Attributes.YEAR);
        return Optional.fromNullable(year);
    }

    @Nonnull
    public String getTrackHash() {
        return get(Attributes.TRACKHASH, "");
    }

    @Nonnull
    public Mode getMode() {
        return get(Attributes.MODE, Mode.STOPPED);
    }

    public double getElapsedTime(boolean estimate) {
        long timeBasis = getTimeBasis();
        double diff = 0;
        if (estimate && timeBasis != 0 && getMode() == PlayerStatus.Mode.PLAYING) {
            diff = (SystemClock.elapsedRealtime() - (double) timeBasis) / 1000;
        }

        double elapsedTime = get(Attributes.ELAPSEDTIME, 0.0d);
        double retval = elapsedTime + diff;

        double totalTime = getTotalTime();
        if (totalTime != 0) {
            retval = Math.min(retval, totalTime);
        }
        return retval;
    }

    private long getTimeBasis() {
        return get(Attributes.TIMEBASIS, 0L);
    }

    public double getTotalTime() {
        return get(Attributes.TOTALTIME, 0.0d);
    }

    @Nonnull
    public Artwork getArtwork() {
        return (Artwork) mAttributes.get(Attributes.ARTWORK.ordinal(), Artwork.missing());
    }

    @Nonnull
    public Optional<String> getTrackNumber() {
        String trackNumber = (String) mAttributes.get(Attributes.TRACKNUMBER.ordinal());
        return Optional.fromNullable(trackNumber);
    }

    public boolean isConnected() {
        return get(Attributes.IS_CONNECTED, false);
    }

    @Nonnull
    public String getPlaylistTimestamp() {
        return get(Attributes.PLAYLIST_TIMESTAMP, "");
    }

    public int getPlaylistTrackCount() {
        return get(Attributes.PLAYLIST_TRACKCOUNT, 0);
    }

    public boolean canSeek() {
        return get(Attributes.IS_SEEKABLE, true);
    }

    public int getPlaylistIndex() {
        return get(Attributes.PLAYLIST_INDEX, 0);
    }

    public boolean isPowered() {
        return get(Attributes.IS_POWERED, false);
    }

    public boolean isMuted() {
        return getVolume() < 0;
    }

    public int getVolume() {
        int retval = get(Attributes.VOLUME, 0);
        return Math.abs(retval);
    }

    @Nullable
    public String getModel() {
        return (String) get(Attributes.MODEL);
    }

    @Nullable
    public String getAddress() {
        return (String) get(Attributes.ADDRESS);
    }

    public boolean isLocalSqueezePlayer() {
        return get(Attributes.IS_LOCAL_SQUEEZEPLAYER, false);
    }

    public boolean isSqueezenetworkCapable() {
        String model = getModel();
        return model != null && !sSqueezenetworkIncapableModels.contains(model);
    }

    @Override
    @Nonnull
    public String toString() {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
        helper.add("initialized", mInitialized);
        helper.add("menuLoadTriggered", mMenuLoadTriggered);

        for (int i = 0; i < mAttributes.size(); i++) {
            int key = mAttributes.keyAt(i);
            Object value = mAttributes.valueAt(i);

            Attributes attr = Attributes.values()[key];
            helper.add(attr.name(), value);
        }
        return helper.toString();
    }

    private void put(SparseArray<Object> attributes, Attributes attr, @Nullable Object value) {
        attributes.put(attr.ordinal(), value);
    }

    private int get(Attributes attr, int defaultValue) {
        return (Integer) mAttributes.get(attr.ordinal(), defaultValue);
    }

    private void put(SparseArray<Object> attributes, Attributes attr, int value) {
        attributes.put(attr.ordinal(), value);
    }

    private long get(Attributes attr, long defaultValue) {
        return (Long) mAttributes.get(attr.ordinal(), defaultValue);
    }

    private void put(SparseArray<Object> attributes, Attributes attr, long value) {
        attributes.put(attr.ordinal(), value);
    }

    private double get(Attributes attr, double defaultValue) {
        return (Double) mAttributes.get(attr.ordinal(), defaultValue);
    }

    private void put(SparseArray<Object> attributes, Attributes attr, double value) {
        attributes.put(attr.ordinal(), value);
    }

    private boolean get(Attributes attr, boolean defaultValue) {
        return (Boolean) mAttributes.get(attr.ordinal(), defaultValue);
    }

    private void put(SparseArray<Object> attributes, Attributes attr, boolean value) {
        attributes.put(attr.ordinal(), value);
    }

    @SuppressWarnings("unchecked")
    private <T> T get(Attributes attr, @Nullable T defaultValue) {
        return (T) mAttributes.get(attr.ordinal(), defaultValue);
    }

    @Nullable
    private Object get(Attributes attr) {
        return mAttributes.get(attr.ordinal());
    }

    @Keep
    static public class ButtonStatus {
        final private boolean mEnabled;
        final private List<String> mCommands;
        final private String mIcon;
        final private String mTooltip;
        final private String mJiveStyle;

        public ButtonStatus(boolean enabled) {
            this(enabled, Collections.emptyList(), null, null, null);
        }

        public ButtonStatus(boolean enabled, List<String> commandList, @Nullable String icon, @Nullable String toolTip, @Nullable String jiveStyle) {
            mEnabled = enabled;
            mCommands = commandList;
            mIcon = icon;
            mTooltip = toolTip;
            mJiveStyle = jiveStyle;
        }

        public void markPressed(PlayerStatus basis) {
            PlayerStatus newStatus = null;
            if (isThumbsDown()) {
                newStatus = basis.withThumbsDownPressed();
            } else if (isThumbsUp()) {
                newStatus = basis.withThumbsUpPressed();
            }
            if (newStatus != null) {
                // new status transaction
                ServerStatus serverStatus = SBContextProvider.get().getServerStatus();
                ServerStatus.Transaction transaction = serverStatus.newTransaction();
                try {
                    transaction.add(newStatus);
                    transaction.markSuccess();
                } finally {
                    transaction.close();
                }

            }
        }

        public boolean isEnabled() {
            return mEnabled;
        }

        @Nonnull
        public List<String> getCommands() {
            return mCommands;
        }

        public String getIcon() {
            return mIcon;
        }

        public String getToolTip() {
            return mTooltip;
        }

        public String getJiveStyle() {
            return mJiveStyle;
        }

        public boolean isThumbsUp() {
            return Objects.equal(mJiveStyle, "thumbsUp") || Objects.equal(mJiveStyle, "love");
        }

        public boolean isThumbsDown() {
            return Objects.equal(mJiveStyle, "thumbsDown") || Objects.equal(mJiveStyle, "hate");
        }

        @Override
        @Nonnull
        public String toString() {
            return MoreObjects.toStringHelper(this).
                    add("enabled", mEnabled).
                    add("commands", mCommands).
                    add("icon", mIcon).
                    add("tooltip", mTooltip).
                    add("jiveStyle", mJiveStyle).
                    toString();
        }
    }

    @Nonnull
    static public Comparator<PlayerStatus> newDefaultComparator() {
        return (lhs, rhs) -> {
            Collator collator = StringTools.getNullSafeCollator();
            return ComparisonChain.start().compare(lhs.getName(), rhs.getName(), collator).compare(lhs.getId(), rhs.getId()).result();
        };
    }

    @Nonnull
    public PlayerStatus withServerStatusUpdate(JsonNode player) {

        SparseArray<Object> override = new SparseArray<>();

        put(override, Attributes.MODEL, player.path("model").asText());
        put(override, Attributes.ADDRESS, player.path("ip").asText());
        put(override, Attributes.IS_CONNECTED, player.path("connected").asInt() != 0);
        put(override, Attributes.NAME, player.path("name").asText());
        put(override, Attributes.IS_LOCAL_SQUEEZEPLAYER, SqueezePlayerHelper.isLocalSqueezePlayer(player));

        JsonNode powerField = player.get("power");
        if (powerField != null && !powerField.isNull()) {
            put(override, Attributes.IS_POWERED, powerField.asInt() != 0);
        }
        return withAttributes(override);
    }

    @Nonnull
    public PlayerStatus withElapsedTime(double elapsedTime) {
        SparseArray<Object> override = new SparseArray<>();
        put(override, Attributes.ELAPSEDTIME, elapsedTime);
        return withAttributes(override);
    }

    @Nonnull
    public PlayerStatus withPlayerStatusUpdate(Context context, long serverId, JsonNode o, long deltaBaseTime) {

        SparseArray<Object> override = new SparseArray<>();
        OSLog.jsonTrace("New player status", o);

        put(override, Attributes.TIMEBASIS, SystemClock.elapsedRealtime() + deltaBaseTime);

        Mode newMode = null;
        String playMode = JsonHelper.getString(o, "mode", null);
        if (playMode != null) {
            newMode = PlayerStatus.Mode.fromCommand(playMode);
        }
        if (newMode == null) {
            newMode = Mode.STOPPED;
        }
        put(override, Attributes.MODE, newMode);

        put(override, Attributes.ELAPSEDTIME, o.path("time").asDouble());
        put(override, Attributes.TOTALTIME, o.path("duration").asDouble());
        put(override, Attributes.PLAYLIST_TIMESTAMP, o.path("playlist_timestamp").asText());
        put(override, Attributes.IS_POWERED, o.path("power").asInt() != 0);
        put(override, Attributes.VOLUME, o.path("mixer volume").asInt());
        put(override, Attributes.PLAYLIST_INDEX, o.path("playlist_cur_index").asInt());
        put(override, Attributes.PLAYLIST_TRACKCOUNT, o.path("playlist_tracks").asInt());
        // default to true
        put(override, Attributes.IS_SEEKABLE, o.path("can_seek").asInt(1) != 0);
        put(override, Attributes.IS_VOLUME_LOCKED, o.path("digital_volume_control").asInt() == 0);

        put(override, Attributes.REMOTE, o.path("remote").asInt() != 0);
        put(override, Attributes.CURRENT_TITLE, o.path("current_title").asText());

        String loadTrackId = null;

        int ndx = o.path("playlist repeat").asInt();
        if (ndx < 0 || ndx >= RepeatMode.values().length) {
            ndx = 0;
        }
        put(override, Attributes.REPEATMODE, RepeatMode.values()[ndx]);

        ndx = o.path("playlist shuffle").asInt();
        if (ndx < 0 || ndx >= ShuffleMode.values().length) {
            ndx = 0;
        }
        put(override, Attributes.SHUFFLEMODE, ShuffleMode.values()[ndx]);

        boolean shouldResetButtonStatus = true;
        JsonNode playlistLoop = o.path("item_loop");
        if (playlistLoop.isArray() && playlistLoop.size() > 0) {
            JsonNode firstItem = playlistLoop.get(0);

            String artist = firstItem.path("artist").asText();
            String track = firstItem.path("track").asText();
            String album = firstItem.path("album").asText();

            // get fallback info from older server versions
            String[] text = firstItem.path("text").asText().split("\n");

            // but apply it selectively
            if (track.length() == 0 && text.length >= 1) {
                track = text[0];
            }
            if (album.length() == 0 && text.length >= 2) {
                album = text[1];
            }
            if (artist.length() == 0 && text.length >= 3) {
                artist = text[2];
            }

            put(override, Attributes.ALBUM, album);
            put(override, Attributes.TRACK, track);
            put(override, Attributes.ARTIST, artist);

            String trackType = firstItem.get("trackType").asText();
            String trackId = JsonHelper.getString(firstItem.path("params"), "track_id", null);
            if (Objects.equal(trackType, "local") && trackId != null) {
                loadTrackId = trackId;
            }

            String newArtworkId = JsonHelper.getString(firstItem, "icon-id", null);
            if (newArtworkId == null) {
                newArtworkId = JsonHelper.getString(firstItem, "coverid", null);
                if (newArtworkId == null) {
                    newArtworkId = JsonHelper.getString(firstItem, "id", null);
                }
            }
            String newArtworkUrl = JsonHelper.getString(firstItem, "artwork_url", null);
            if (newArtworkUrl == null) {
                newArtworkUrl = JsonHelper.getString(firstItem, "icon", null);
            }

            Artwork artwork = getArtwork();
            // has artwork changed id's or url or availability?
            if (!artwork.isEquivalent(newArtworkId, newArtworkUrl)) {
                // yes, it has

                // scan any known players for this same artwork (sync'd situations)
                Artwork newArtwork = null;

                for (PlayerStatus p : SBContextProvider.get().getServerStatus().getAvailablePlayers()) {
                    Artwork playerArtwork = p.getArtwork();
                    if (playerArtwork.isEquivalent(newArtworkId, newArtworkUrl)) {
                        newArtwork = playerArtwork;
                        break;
                    }
                }

                if (newArtwork == null) {
                    newArtwork = Artwork.getInstance(context, getId(), newArtworkId, newArtworkUrl);
                }
                put(override, Attributes.ARTWORK, newArtwork);
            }
            JsonNode buttons = o.path("remoteMeta").path("buttons");
            if (buttons.isObject()) {
                for (PlayerButton p : PlayerButton.values()) {
                    JsonNode b = buttons.get(p.getKey());
                    if (b == null) {
                        // if the key doesn't exist, assume the
                        // buttons
                        // are valid
                        setButtonStatus(override, p, new ButtonStatus(true));
                    } else if (b.isObject()) {
                        List<String> commandList = new ArrayList<>();
                        JsonNode commands = b.path("command");
                        if (commands.isArray()) {
                            for (int i = 0; i < commands.size(); i++) {
                                commandList.add(commands.get(i).asText());
                            }
                            String icon = JsonHelper.getString(b, "icon", null);
                            String toolTip = JsonHelper.getString(b, "tooltip", null);
                            String jiveStyle = JsonHelper.getString(b, "jiveStyle", null);

                            setButtonStatus(override, p, new ButtonStatus(true, commandList, icon, toolTip, jiveStyle));
                        }
                    } else if (b.isBoolean() || b.isNumber()) {
                        setButtonStatus(override, p, new ButtonStatus(b.asInt() != 0));
                    } else if (b.isTextual()) {
                        setButtonStatus(override, p, new ButtonStatus(b.asInt(0) != 0));
                    } else {
                        Reporting.report("Unexpected value type: " + o);
                    }
                }
                shouldResetButtonStatus = false;
            }
        } else {
            put(override, Attributes.ALBUM, "");
            put(override, Attributes.TRACK, "");
            put(override, Attributes.ARTIST, "");
            put(override, Attributes.ARTWORK, Artwork.missing());
        }
        if (shouldResetButtonStatus) {
            internalSetDefaultButtonStatus(override);
        }

        // check for track boundary
        String newTrackHash = calculateTrackHash(override);
        String oldTrackHash = getTrackHash();
        if (!oldTrackHash.equals(newTrackHash)) {
            put(override, Attributes.IS_THUMBSDOWN_PRESSED, false);
            put(override, Attributes.IS_THUMBSUP_PRESSED, false);
            put(override, Attributes.TRACKHASH, newTrackHash);
        }

        String oldTrackId = getTrackId().orNull();
        if(!Objects.equal(oldTrackId, loadTrackId)) {
            clearTrackInfo(override, newTrackHash);
            if (loadTrackId != null) {
                put(override, Attributes.TRACKID, loadTrackId);

                TrackInfo ti = TrackInfo.peek(serverId, loadTrackId).orNull();
                if (ti != null) {
                    applyTrackInfo(override, ti);
                }
            }

        }
        return withAttributes(override);
    }

    private void clearTrackInfo(SparseArray<Object> override, String newTrackHash) {
        put(override, Attributes.TRACKINFO, null);
        put(override, Attributes.YEAR, null);
        put(override, Attributes.TRACKNUMBER, null);
        put(override, Attributes.ARTISTID, null);
        put(override, Attributes.ALBUMID, null);
    }

    @Nonnull
    public PlayerStatus withShuffleMode(ShuffleMode shuffleMode) {
        SparseArray<Object> override = new SparseArray<>(1);
        put(override, Attributes.SHUFFLEMODE, shuffleMode);

        return withAttributes(override);
    }

    @Nonnull
    public PlayerStatus withRepeatMode(RepeatMode repeatMode) {
        SparseArray<Object> override = new SparseArray<>(1);
        put(override, Attributes.REPEATMODE, repeatMode);

        return withAttributes(override);
    }

    @Nonnull
    public PlayerStatus withTrackInfo(TrackInfo trackInfo) {
        SparseArray<Object> override = new SparseArray<>(1);

        applyTrackInfo(override, trackInfo);

        return withAttributes(override);
    }

    public boolean needsTrackLookup() {
        return get(Attributes.TRACKINFO) == null && getTrackId().isPresent();
    }

    protected void applyTrackInfo(SparseArray<Object> override, TrackInfo trackInfo) {
        put(override, Attributes.TRACKINFO, trackInfo);

        String trackNum = trackInfo.getTrackNumber().orNull();
        if (trackNum != null) {
            put(override, Attributes.TRACKNUMBER, trackNum);
        }

        String artistId = trackInfo.getArtistId().orNull();
        if (artistId != null) {
            put(override, Attributes.ARTISTID, artistId);
        }

        String albumId = trackInfo.getAlbumId().orNull();
        if (albumId != null) {
            put(override, Attributes.ALBUMID, albumId);
        }

        String year = trackInfo.getYear().orNull();
        if (year != null) {
            put(override, Attributes.YEAR, year);
        }
    }

    @Nonnull
    public PlayerStatus withThumbsUpPressed() {
        SparseArray<Object> override = new SparseArray<>(2);
        put(override, Attributes.IS_THUMBSUP_PRESSED, true);
        put(override, Attributes.IS_THUMBSDOWN_PRESSED, false);

        return withAttributes(override);
    }

    @Nonnull
    public PlayerStatus withThumbsDownPressed() {
        SparseArray<Object> override = new SparseArray<>(2);
        put(override, Attributes.IS_THUMBSDOWN_PRESSED, true);
        put(override, Attributes.IS_THUMBSUP_PRESSED, false);
        return withAttributes(override);
    }

    private PlayerStatus withAttributes(SparseArray<Object> newAttributes) {
        boolean equivalent = true;

        int size = newAttributes.size();
        for (int i = 0; i < size; i++) {
            int key = newAttributes.keyAt(i);
            Object newValue = newAttributes.valueAt(i);
            Object oldValue = mAttributes.get(key);
            if (!Objects.equal(newValue, oldValue)) {
                equivalent = false;
                break;
            }
        }

        if (equivalent) {
            return this;
        } else {
            return new PlayerStatus(this, newAttributes);
        }
    }

    /**
     * used to calculate track boundaries
     */
    @Nonnull
    private String calculateTrackHash(SparseArray<Object> override) {
        PlayerStatus temp = withAttributes(override);

        String trackId = temp.getTrackId().orNull();
        if (trackId == null) {
            trackId = temp.getTrack() + temp.getDisplayArtist() + temp.getTrackNumber().or("-");
        }
        String retval = trackId + temp.getPlaylistIndex() + temp.getPlaylistTimestamp();

        return retval;
    }

    @Nonnull
    private EnumMap<PlayerButton, ButtonStatus> initButtonStatus(SparseArray<Object> attributes) {
        @SuppressWarnings("unchecked")
        EnumMap<PlayerButton, ButtonStatus> status = (EnumMap<PlayerButton, ButtonStatus>) attributes.get(Attributes.BUTTONSTATUS_MAP.ordinal());
        if (status == null) {
            status = new EnumMap<>(PlayerButton.class);
            put(attributes, Attributes.BUTTONSTATUS_MAP, status);
        }
        return status;
    }

    private void setButtonStatus(SparseArray<Object> attributes, PlayerButton button, ButtonStatus buttonStatus) {
        initButtonStatus(attributes).put(button, buttonStatus);
    }

    private void internalSetDefaultButtonStatus(SparseArray<Object> attributes) {
        EnumMap<PlayerButton, ButtonStatus> status = initButtonStatus(attributes);
        status.clear();
        for (PlayerButton p : PlayerButton.values()) {
            status.put(p, new ButtonStatus(true));
        }
    }
}
