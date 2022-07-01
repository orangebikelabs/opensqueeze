/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.menu;

import androidx.annotation.Keep;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.orangebikelabs.orangesqueeze.browse.common.IconRetriever;
import com.orangebikelabs.orangesqueeze.browse.common.Item;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.OSAssert;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
@NotThreadSafe
public class MenuElement implements Serializable {

    @Nonnull
    static public MenuElement get(JsonNode node, @Nullable MenuBase menuBase) throws IOException {
        OSAssert.assertNotMainThread();

        MenuElement element = JsonHelper.getJsonObjectReader().forType(MenuElement.class).readValue(node.traverse());
        element.mMenuBase = menuBase;
        return element;
    }

    @JsonIgnore
    @Nonnull
    static public IconRetriever newIconRetriever() {
        return new MenuElementIconRetriever(new Function<Item, MenuElement>() {
            @Override
            @Nullable
            public MenuElement apply(@Nullable Item item) {
                MenuElement retval = null;
                if (item instanceof StandardMenuItem) {
                    retval = ((StandardMenuItem) item).getMenuElement();
                }
                return retval;
            }
        });
    }

    @JsonIgnore
    @Nonnull
    static public IconRetriever newIconRetriever(final Supplier<MenuElement> supplier) {
        return new MenuElementIconRetriever(new Function<Item, MenuElement>() {
            @Override
            @Nullable
            public MenuElement apply(@Nullable Item item) {
                return supplier.get();
            }
        });
    }

    final static private ImmutableSet<String> sBlacklist;

    static {
        sBlacklist = ImmutableSet.of("playerpower", "opmlappgallery", "globalSearch", "myMusicSearch", "opmlsearch");
    }

    final private static String ARTIST_ID_KEY = "artist_id";
    final private static String TRACK_ID_KEY = "track_id";

    @Keep
    @JsonIgnoreProperties(ignoreUnknown = true)
    static public class Input implements Serializable {

        @Nullable
        private Map<String, String> mProcessingPopup;

        @Nonnull
        private String mTitle = "";

        @Nonnull
        private String mInitialText = "";

        @Nullable
        private String mInputStyle;

        @Nullable
        public Map<String, String> getProcessingPopup() {
            return mProcessingPopup;
        }

        public void setProcessingPopup(@Nullable Map<String, String> processingPopup) {
            mProcessingPopup = processingPopup;
        }

        @JsonGetter("_inputStyle")
        @Nullable
        public String getInputStyle() {
            return mInputStyle;
        }

        @JsonSetter("_inputStyle")
        public void setInputStyle(@Nullable String inputStyle) {
            mInputStyle = inputStyle;
        }

        @Nonnull
        public String getInitialText() {
            return mInitialText;
        }

        public void setInitialText(@Nullable String initialText) {
            mInitialText = Strings.nullToEmpty(initialText);
        }

        @Nonnull
        public String getTitle() {
            return mTitle;
        }

        public void setTitle(@Nullable String title) {
            mTitle = Strings.nullToEmpty(title);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(mProcessingPopup, mTitle, mInitialText, mInputStyle);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Input other = (Input) obj;
            // @formatter:off
            return Objects.equal(mProcessingPopup, other.mProcessingPopup)
                    && Objects.equal(mTitle, other.mTitle)
                    && Objects.equal(mInputStyle, other.mInputStyle)
                    && Objects.equal(mInitialText, other.mInitialText);
            // @formatter:on
        }

        @Override
        @Nonnull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("processingPopup", mProcessingPopup)
                    .add("title", mTitle)
                    .add("inputStyle", mInputStyle)
                    .add("initialText", mInitialText)
                    .toString();
        }
    }

    private final Map<String, Object> mUnknownProperties = new HashMap<>();

    @JsonIgnore
    @Nullable
    private MenuBase mMenuBase;

    @Nullable
    private String mTextkey;

    @Nonnull
    private String mText = "";

    @Nonnull
    private String mTitle = "";

    @Nonnull
    private String mArtist = "";

    @Nonnull
    private String mYear = "";

    @Nullable
    private String mOnClick;

    @Nullable
    private String mIcon;

    @Nullable
    private String mStyle;

    @Nullable
    private String mAction;

    @Nullable
    private String mWeblink;

    @Nullable
    private String mGoAction, mAddAction, mPlayAction, mPlayHoldAction;

    private int mShowBigArtwork;

    @JsonProperty(value = "icon-id")
    private String mIconId;

    @Nullable
    private String mType;

    @Nullable
    private Integer mRadio;

    @Nullable
    private Integer mCheckbox;

    @Nullable
    private String mNextWindow;

    @Nullable
    private Input mInput;

    @Nullable
    private String mId;

    @Nullable
    private String mNode;
    private double mWeight;
    private int mDisplayWhenOff;
    private int mIsANode;

    @Nullable
    private String mMenuIcon;

    @Nullable
    private String mHomeMenuText;

    @Nullable
    private String mIconStyle;

    // settings added for sliders
    private int mInitialSliderValue;
    private int mMaxSliderValue;
    private int mMinSliderValue;
    private boolean mIsSlider;

    @Nullable
    private String mSliderIcons;

    @Nullable
    private Integer mSelectedIndex;

    @Nonnull
    private List<String> mChoiceStrings = Collections.emptyList();

    @Nonnull
    private Map<String, MenuAction> mActions = Collections.emptyMap();

    @Nonnull
    private List<MenuElement> mSubelementList = Collections.emptyList();

    @JsonIgnore
    @Nullable
    public MenuBase getMenuBase() {
        return mMenuBase;
    }

    @JsonSetter("item_loop")
    public void setSubelementList(@Nullable List<MenuElement> list) {
        if (list == null) {
            mSubelementList = Collections.emptyList();
        } else {
            mSubelementList = list;
        }
    }

    @JsonGetter("item_loop")
    @Nonnull
    public List<MenuElement> getSubelementList() {
        return mSubelementList;
    }

    @Nullable
    public String getId() {
        return mId;
    }

    public void setId(@Nullable String id) {
        mId = id;
    }

    @Nonnull
    public String getArtist() {
        return mArtist;
    }

    public void setArtist(@Nullable String artist) {
        mArtist = Strings.nullToEmpty(artist);
    }

    @Nonnull
    public String getYear() {
        return mYear;
    }

    public void setYear(@Nullable String year) {
        mYear = Strings.nullToEmpty(year);
    }

    @JsonIgnore
    @Nonnull
    public Optional<Integer> getSelectedIndex() {
        return Optional.fromNullable(mSelectedIndex);
    }

    @JsonGetter("selectedIndex")
    public int _getter_getSelectedIndex() {
        if (mSelectedIndex == null) {
            return 0;
        } else {
            return mSelectedIndex + 1;
        }
    }

    @JsonSetter("selectedIndex")
    public void setSelectedIndex(int selectedIndex) {
        if (selectedIndex > 0) {
            mSelectedIndex = selectedIndex - 1;
        } else {
            mSelectedIndex = null;
        }
    }

    public void setChoiceStrings(@Nullable List<String> choiceStrings) {
        if (choiceStrings == null) {
            mChoiceStrings = Collections.emptyList();
        } else {
            mChoiceStrings = choiceStrings;
        }
    }

    public void setOnClick(@Nullable String onClick) {
        mOnClick = onClick;
    }

    @Nullable
    public String getOnClick() {
        return mOnClick;
    }

    @JsonIgnore
    public boolean isChoice() {
        return !mChoiceStrings.isEmpty() && mSelectedIndex != null;
    }

    @JsonIgnore
    public boolean isYear() {
        if (mText.matches("^\\d+$") && mText.length() == 4 && getMenuIcon() == null && getIcon() == null && getIconId() == null) {
            return true;
        }
        return false;
    }

    @Nonnull
    public List<String> getChoiceStrings() {
        return mChoiceStrings;
    }

    @Nullable
    public String getIconStyle() {
        return mIconStyle;
    }

    public void setIconStyle(@Nullable String iconStyle) {
        mIconStyle = iconStyle;
    }

    @Nullable
    public String getHomeMenuText() {
        return mHomeMenuText;
    }

    public void setHomeMenuText(@Nullable String homeMenuText) {
        mHomeMenuText = homeMenuText;
    }

    @Nullable
    public String getNode() {
        return mNode;
    }

    public void setNode(@Nullable String node) {
        mNode = node;
    }

    public double getWeight() {
        return mWeight;
    }

    public void setWeight(double weight) {
        mWeight = weight;
    }

    public int getDisplayWhenOff() {
        return mDisplayWhenOff;
    }

    public void setDisplayWhenOff(int displayWhenOff) {
        mDisplayWhenOff = displayWhenOff;
    }

    @JsonGetter("initial")
    public int getSliderInitialValue() {
        return mInitialSliderValue;
    }

    @JsonSetter("initial")
    public void setSliderInitialValue(Object initial) {
        if (initial instanceof Number) {
            mInitialSliderValue = ((Number) initial).intValue();
        } else if (initial instanceof String) {
            try {
                mInitialSliderValue = Integer.parseInt((String) initial);
            } catch (NumberFormatException e) {
                mInitialSliderValue = 0;
            }
        } else {
            mInitialSliderValue = 0;
        }
    }

    @JsonGetter("max")
    public int getSliderMaxValue() {
        return mMaxSliderValue;
    }

    @JsonSetter("max")
    public void setSliderMaxValue(int max) {
        mMaxSliderValue = max;
    }

    @JsonGetter("min")
    public int getSliderMinValue() {
        return mMinSliderValue;
    }

    @JsonSetter("min")
    public void setSliderMinValue(int min) {
        mMinSliderValue = min;
    }

    @JsonGetter("slider")
    public int _internal_getSlider() {
        return mIsSlider ? 1 : 0;
    }

    @JsonIgnore
    public boolean isSlider() {
        return mIsSlider;
    }

    @JsonSetter("slider")
    public void _internal_setSlider(int slider) {
        mIsSlider = slider != 0;
    }

    @Nullable
    public String getSliderIcons() {
        return mSliderIcons;
    }

    public void setSliderIcons(@Nullable String sliderIcons) {
        mSliderIcons = sliderIcons;
    }

    @JsonIgnore
    public boolean isANode() {
        return mIsANode != 0;
    }

    @JsonIgnore
    public void setIsANode(boolean val) {
        mIsANode = val ? 1 : 0;
    }

    @JsonGetter("isANode")
    public int _getter_isANode() {
        return mIsANode;
    }

    @JsonSetter("isANode")
    public void _setter_isAnode(int isANode) {
        mIsANode = isANode;
    }

    @Nullable
    public String getWeblink() {
        return mWeblink;
    }

    public void setWeblink(@Nullable String link) {
        mWeblink = link;
    }

    @Nullable
    public String getMenuIcon() {
        return mMenuIcon;
    }

    public void setMenuIcon(@Nullable String menuIcon) {
        mMenuIcon = menuIcon;
    }

    @Nullable
    public String getTextkey() {
        String retval = mTextkey;
        if (retval == null) {
            Object key = getParams().get("textkey");
            retval = (key == null ? null : key.toString());
        }
        return retval;
    }

    public void setTextkey(@Nullable String textkey) {
        mTextkey = textkey;
    }

    @Nonnull
    public String getTitle() {
        return mTitle;
    }

    @Nonnull
    public String getText() {
        OSAssert.assertNotNull(mText, "text should always be non-null");
        return mText;
    }

    /**
     * return first line of text field
     */
    @JsonIgnore
    @Nonnull
    public String getText1() {
        String text = getText();

        int ndx = text.indexOf('\n');
        if (ndx == -1) {
            return text;
        } else {
            return text.substring(0, ndx);
        }
    }

    /**
     * return second line of text field
     */
    @JsonIgnore
    @Nullable
    public String getText2() {
        if (!isChoice()) {
            String text = getText();
            int ndx = text.indexOf('\n');
            if (ndx == -1) {
                return null;
            } else {
                return text.substring(ndx + 1);
            }
        } else {
            int ndx = mSelectedIndex == null ? 0 : mSelectedIndex;
            if (ndx < 0 || ndx >= getChoiceStrings().size()) {
                return "";
            } else {
                return getChoiceStrings().get(ndx);
            }
        }
    }

    public void setText(@Nullable String text) {
        mText = Strings.nullToEmpty(text);
    }

    public void setTitle(@Nullable String title) {
        mTitle = Strings.nullToEmpty(title);
    }

    @Nullable
    public String getIcon() {
        return mIcon;
    }

    @JsonSetter("icon")
    public void setIcon(@Nullable Object icon) {
        if (icon instanceof CharSequence) {
            mIcon = icon.toString();
        } else {
            mIcon = null;
        }
    }

    @Nullable
    public String getStyle() {
        return mStyle;
    }

    public void setStyle(@Nullable String style) {
        mStyle = style;
    }

    @Nullable
    public String getAction() {
        return mAction;
    }

    public void setAction(@Nullable String action) {
        mAction = action;
    }

    @Nullable
    public String getGoAction() {
        return mGoAction;
    }

    public void setGoAction(@Nullable String goAction) {
        mGoAction = goAction;
    }

    @Nullable
    public String getAddAction() {
        return mAddAction;
    }

    public void setAddAction(@Nullable String addAction) {
        mAddAction = addAction;
    }

    @Nullable
    public String getPlayAction() {
        return mPlayAction;
    }

    public void setPlayAction(@Nullable String playAction) {
        mPlayAction = playAction;
    }

    @Nullable
    public String getPlayHoldAction() {
        return mPlayHoldAction;
    }

    public void setPlayHoldAction(@Nullable String playHoldAction) {
        mPlayHoldAction = playHoldAction;
    }

    public int getShowBigArtwork() {
        return mShowBigArtwork;
    }

    public void setShowBigArtwork(int showBigArtwork) {
        mShowBigArtwork = showBigArtwork;
    }

    @Nullable
    public String getIconId() {
        String retval = mIconId;
        if (retval == null) {
            Object key = getWindow().get("icon-id");
            retval = (key == null ? null : key.toString());
        }
        return retval;
    }

    public void setIconId(@Nullable String iconId) {
        mIconId = iconId;
    }

    @Nullable
    public String getType() {
        return mType;
    }

    public void setType(@Nullable String type) {
        mType = type;
    }

    @JsonIgnore
    public boolean isCheckbox() {
        return mCheckbox != null;
    }

    @JsonIgnore
    public boolean isCheckboxChecked() {
        return mCheckbox != null && mCheckbox != 0;
    }

    @JsonGetter("checkbox")
    @Nullable
    public Integer getCheckbox() {
        return mCheckbox;
    }

    @JsonGetter("checkbox")
    public void setCheckbox(@Nullable Integer checkbox) {
        mCheckbox = checkbox;
    }

    @JsonIgnore
    public boolean isRadio() {
        return mRadio != null;
    }

    @JsonIgnore
    public boolean isRadioChecked() {
        return mRadio != null && mRadio != 0;
    }

    @JsonGetter("radio")
    @Nullable
    public Integer getRadio() {
        return mRadio;
    }

    @JsonSetter("radio")
    public void setRadio(@Nullable Integer radio) {
        mRadio = radio;
    }

    @Nullable
    public String getNextWindow() {
        return mNextWindow;
    }

    public void setNextWindow(@Nullable String nextWindow) {
        mNextWindow = nextWindow;
    }

    @Nullable
    public Input getInput() {
        return mInput;
    }

    public void setInput(@Nullable Input input) {
        mInput = input;
    }

    @Nonnull
    public Map<String, MenuAction> getActions() {
        return mActions;
    }

    @Nonnull
    public Map<String, MenuAction> getBaseActions() {
        Map<String, MenuAction> retval = null;
        if (mMenuBase != null) {
            retval = mMenuBase.getActions();
        }
        if (retval == null) {
            retval = Collections.emptyMap();
        }
        return retval;
    }

    public void setActions(@Nullable Map<String, MenuAction> actions) {
        if (actions == null) {
            mActions = Collections.emptyMap();
        } else {
            mActions = new LinkedHashMap<>();

            // create copy of supplied actions
            for (Map.Entry<String, MenuAction> e : actions.entrySet()) {
                String actionName = e.getKey();
                MenuAction value = e.getValue();
                if (value != null) {
                    // update MenuAction with action name
                    value = value.withName(actionName);
                }
                mActions.put(actionName, value);
            }
        }
    }

    @JsonIgnore
    public boolean isVariousArtist() {
        return getParams().containsKey("variousartist");
    }

    @JsonIgnore
    public boolean isArtist() {
        boolean retval = getArtistId() != null;
        if (retval) {
            // only an artist if there are not other subfiltering id's
            boolean containsOthers = containsIdParamsOtherThan(getParams(), ARTIST_ID_KEY);
            if (!containsOthers) {
                containsOthers = containsIdParamsOtherThan(getCommonParams(), ARTIST_ID_KEY);
            }
            retval = !containsOthers;
        }
        return retval;
    }

    private boolean containsIdParamsOtherThan(@Nullable Map<String, Object> params, String otherThan) {
        boolean retval = false;
        if (params != null) {
            for (String p : params.keySet()) {
                if (p.endsWith("_id") && !p.equals(otherThan)) {
                    retval = true;
                    break;
                }
            }
        }
        return retval;
    }

    @JsonIgnore
    public boolean isAlbum() {
        return getAlbumId() != null;
    }

    @JsonIgnore
    @Nullable
    public String getAlbumId() {
        Object retval;
        // stock server
        retval = getCommonParams().get("album_id");
        if (retval == null) {
            retval = getParams().get("album_id");
        }
        return retval == null ? null : retval.toString();
    }

    @JsonIgnore
    @Nullable
    public String getArtistId() {
        Object retval;
        // stock server
        retval = getCommonParams().get(ARTIST_ID_KEY);
        if (retval == null) {
            retval = getParams().get(ARTIST_ID_KEY);
            if (retval == null) {
                // custombrowse version
                if ("artist".equals(getType())) {
                    retval = getParams().get("artist");
                }
            }
        }
        return retval == null ? null : retval.toString();
    }

    @JsonIgnore
    public boolean isTrack() {
        return getTrackId() != null;
    }

    @JsonIgnore
    @Nullable
    public String getTrackId() {
        Object retval;
        // stock server
        retval = getCommonParams().get(TRACK_ID_KEY);
        if (retval == null) {
            retval = getParams().get(TRACK_ID_KEY);
        }

        if (retval == null) {
            MenuAction moreAction = MenuHelpers.getAction(this, ActionNames.MORE);
            if (moreAction != null) {
                retval = moreAction.getParams().get(TRACK_ID_KEY);
            }
        }

        if (retval == null) {
            // trackstat variation
            retval = getNamedParamsNonnull("baseparams").get("track.id");
        }

        //				// custombrowse version
        //				if ("artist".equals(getType())) {
        //					retval = getParams().get("artist");
        //				}
        return retval == null ? null : retval.toString();
    }

    @JsonIgnore
    @Nonnull
    public Map<String, Object> getCommonParams() {
        return getNamedParamsNonnull("commonParams");
    }

    @JsonIgnore
    @Nonnull
    public Map<String, Object> getParams() {
        return getNamedParamsNonnull("params");
    }

    @JsonIgnore
    @Nonnull
    public Map<String, Object> getWindow() {
        return getNamedParamsNonnull("window");
    }

    @JsonIgnore
    @Nullable
    public Map<String, Object> getNamedParams(String name) {
        return getNamedMap(name);
    }

    @JsonIgnore
    @Nonnull
    public Map<String, Object> getNamedParamsNonnull(String name) {
        Map<String, Object> retval = getNamedMap(name);
        if (retval == null) {
            retval = Collections.emptyMap();
        }
        return retval;
    }


    @JsonIgnore
    public boolean isBlacklisted() {
        return sBlacklist.contains(mId);
    }

    @SuppressWarnings("unchecked")
    @JsonIgnore
    @Nullable
    protected Map<String, Object> getNamedMap(String name) {
        Object o = mUnknownProperties.get(name);
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        } else {
            return null;
        }
    }

    @JsonAnySetter
    public void handleUnknown(String key, Object value) {
        mUnknownProperties.put(key, value);
    }

    /**
     * returns whether or not this item can trigger the context menu
     */
    @JsonIgnore
    public boolean canShowContextMenu() {
        boolean retval = false;

        MenuAction more = MenuHelpers.getAction(this, ActionNames.MORE);
        if (more != null) {
            Map<String, ?> moreParams = MenuHelpers.buildItemParameters(this, more);
            if (moreParams != null) {
                retval = true;
            }
        }
//        else {
//            Set<MenuAction> actionList = ContextMenuAction.secondaryActionList(this);
//            retval = !actionList.isEmpty();
//        }
        return retval;
    }

    @JsonIgnore
    public boolean isPlayItem() {
        boolean retval = false;

        if (Objects.equal(getStyle(), StyleNames.ITEMPLAY)) {
            retval = true;
        } else if (Objects.equal(getGoAction(), ActionNames.PLAY)) {
            MenuBase base = getMenuBase();
            if (getActions().containsKey(ActionNames.PLAY)) {
                retval = true;
            } else if (base != null && base.getActions().containsKey(ActionNames.PLAY)) {
                retval = true;
            }
        }
        if (retval) {
            // items that are style=itemplay but have a single action "go" are not play items
            if (mActions.size() == 1 && mActions.containsKey(ActionNames.GO)) {
                retval = false;
            }
        }
        return retval;
    }

    @Override
    @JsonIgnore
    public int hashCode() {
        // @formatter:off
        return Objects.hashCode(mUnknownProperties, mMenuBase, mTextkey, mText, mTitle, mArtist, mYear,
                mIcon, mStyle, mAction, mGoAction, mAddAction, mPlayAction, mPlayHoldAction, mShowBigArtwork,
                mIconId, mType, mCheckbox, mNextWindow, mInput, mId, mNode, mWeight, mDisplayWhenOff, mIsANode,
                mMenuIcon, mHomeMenuText, mIconStyle, mSelectedIndex, mActions, mRadio, mChoiceStrings, mInitialSliderValue,
                mMaxSliderValue, mMinSliderValue, mIsSlider, mSliderIcons

        );
        // @formatter:on
    }

    @Override
    @JsonIgnore
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MenuElement other = (MenuElement) obj;

        // @formatter:off
        return Objects.equal(mUnknownProperties, other.mUnknownProperties)
                && Objects.equal(mMenuBase, other.mMenuBase)
                && Objects.equal(mTextkey, other.mTextkey)
                && Objects.equal(mText, other.mText)
                && Objects.equal(mTitle, other.mTitle)
                && Objects.equal(mArtist, other.mArtist)
                && Objects.equal(mYear, other.mYear)
                && Objects.equal(mIcon, other.mIcon)
                && Objects.equal(mStyle, other.mStyle)
                && Objects.equal(mAction, other.mAction)
                && Objects.equal(mGoAction, other.mGoAction)
                && Objects.equal(mAddAction, other.mAddAction)
                && Objects.equal(mPlayAction, other.mPlayAction)
                && Objects.equal(mPlayHoldAction, other.mPlayHoldAction)
                && Objects.equal(mShowBigArtwork, other.mShowBigArtwork)
                && Objects.equal(mIconId, other.mIconId)
                && Objects.equal(mType, other.mType)
                && Objects.equal(mCheckbox, other.mCheckbox)
                && Objects.equal(mNextWindow, other.mNextWindow)
                && Objects.equal(mInput, other.mInput)
                && Objects.equal(mId, other.mId)
                && Objects.equal(mNode, other.mNode)
                && Objects.equal(mWeight, other.mWeight)
                && Objects.equal(mDisplayWhenOff, other.mDisplayWhenOff)
                && Objects.equal(mIsANode, other.mIsANode)
                && Objects.equal(mMenuIcon, other.mMenuIcon)
                && Objects.equal(mHomeMenuText, other.mHomeMenuText)
                && Objects.equal(mIconStyle, other.mIconStyle)
                && Objects.equal(mSelectedIndex, other.mSelectedIndex)
                && Objects.equal(mActions, other.mActions)
                && Objects.equal(mRadio, other.mRadio)
                && Objects.equal(mChoiceStrings, other.mChoiceStrings)
                && Objects.equal(mInitialSliderValue, other.mInitialSliderValue)
                && Objects.equal(mMaxSliderValue, other.mMaxSliderValue)
                && Objects.equal(mMinSliderValue, other.mMinSliderValue)
                && Objects.equal(mIsSlider, other.mIsSlider)
                && Objects.equal(mSliderIcons, other.mSliderIcons);
        // @formatter:on
    }

    @Override
    @JsonIgnore
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("base", mMenuBase).
                add("textKey", mTextkey).
                add("text", mText).
                add("unknown", mUnknownProperties).
                add("title", mTitle).
                add("artist", mArtist).
                add("year", mYear).
                add("icon", mIcon).
                add("style", mStyle).
                add("action", mAction).
                add("goAction", mGoAction).
                add("addAction", mAddAction).
                add("playAction", mPlayAction).
                add("playHoldAction", mPlayHoldAction).
                add("showBigArtwork", mShowBigArtwork).
                add("iconId", mIconId).
                add("type", mType).
                add("checkbox", mCheckbox).
                add("nextWindow", mNextWindow).
                add("input", mInput).
                add("id", mId).
                add("node", mNode).
                add("weight", mWeight).
                add("displayWhenOff", mDisplayWhenOff).
                add("isANode", mIsANode).
                add("menuIcon", mMenuIcon).
                add("homeMenuText", mHomeMenuText).
                add("iconStyle", mIconStyle).
                add("selectedIndex", mSelectedIndex).
                add("radio", mRadio).
                add("choiceStrings", mChoiceStrings).

                omitNullValues().

                toString();
    }
}
