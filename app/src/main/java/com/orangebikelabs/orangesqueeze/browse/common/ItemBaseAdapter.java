/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import android.content.Context;

import androidx.annotation.LayoutRes;
import androidx.core.content.ContextCompat;

import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.google.android.material.slider.Slider;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.nhaarman.listviewanimations.util.Swappable;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.AutoSizeTextHelper;
import com.orangebikelabs.orangesqueeze.artwork.ListPreloadFragment.PreloadAdapter;
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor;
import com.orangebikelabs.orangesqueeze.browse.OSBrowseAdapter;
import com.orangebikelabs.orangesqueeze.common.Drawables;
import com.orangebikelabs.orangesqueeze.common.MoreMath;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.event.ItemActionButtonClickEvent;
import com.orangebikelabs.orangesqueeze.common.event.ItemSliderChangedEvent;
import com.orangebikelabs.orangesqueeze.menu.MenuElement;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * @author tbsandee@orangebikelabs.com
 */
abstract public class ItemBaseAdapter extends BaseAdapter implements SectionIndexer, PreloadAdapter, OSBrowseAdapter, Swappable {

    public enum IconVisibility {
        VISIBLE(View.VISIBLE), GONE(View.GONE), INVISIBLE(View.INVISIBLE);

        final private int mValue;

        IconVisibility(int value) {
            mValue = value;
        }

        public int getVisibility() {
            return mValue;
        }
    }

    final private List<Item> mItems = new ArrayList<>();

    // this may be accessed from multiple threads
    final private ConcurrentLinkedQueue<Item> mPreloadItems = new ConcurrentLinkedQueue<>();

    final protected Context mContext;

    final protected ThumbnailProcessor mThumbnailProcessor;

    final protected AutoSizeTextHelper mAutoSizeHelper = new AutoSizeTextHelper();

    protected SectionIndexer mIndexer;
    protected boolean mSorted;
    protected boolean mNotifyOnChange = true;


    /**
     * ensure that backing list is threadsafe, since we access it from other threads for preloading, etc.
     */
    protected ItemBaseAdapter(Context context, ThumbnailProcessor thumbnailProcessor) {
        mContext = context;
        mThumbnailProcessor = thumbnailProcessor;
    }

    @Override
    public void setSorted(boolean sorted) {
        mSorted = sorted;
    }

    public boolean isSorted() {
        return mSorted;
    }

    @Override
    final public Item getItem(int position) {
        OSAssert.assertMainThread();

        return mItems.get(position);
    }

    public void remove(int position) {
        OSAssert.assertMainThread();

        Item removed = mItems.remove(position);
        if (removed != null) {
            mPreloadItems.remove(removed);
        }

        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void swapItems(int i1, int i2) {
        OSAssert.assertMainThread();

        // guard against bad inputs
        if (i1 < 0 || i1 >= getCount()) {
            return;
        }
        if (i2 < 0 || i2 >= getCount()) {
            return;
        }

        Item old1 = mItems.get(i1);
        Item old2 = mItems.get(i2);
        mItems.set(i2, old1);
        mItems.set(i1, old2);

        // we're specifically NOT calling notifyDataSetChanged() because this is called from within the DynamicListView code.
    }

    @Override
    public void notifyDataSetChanged() {
        OSAssert.assertMainThread();

        // ensure we rebuild the indexer with the new cursor
        mIndexer = null;

        super.notifyDataSetChanged();

        mNotifyOnChange = true;
    }

    @Override
    public void setNotifyOnChange(boolean notifyOnChange) {
        OSAssert.assertMainThread();
        mNotifyOnChange = notifyOnChange;
    }

    public void remove(Item item) {
        OSAssert.assertMainThread();
        mItems.remove(item);
        mPreloadItems.remove(item);

        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    public void insert(Item item, int ndx) {
        OSAssert.assertMainThread();
        mItems.add(ndx, item);

        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void clear() {
        OSAssert.assertMainThread();

        mItems.clear();
        mPreloadItems.clear();

        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void addAll(Collection<? extends Item> collection) {
        OSAssert.assertMainThread();

        mItems.addAll(collection);

        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    public void addAll(Item... items) {
        addAll(Arrays.asList(items));
    }

    @Override
    public void add(Item i) {
        OSAssert.assertMainThread();
        mItems.add(i);

        if (mNotifyOnChange) {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        OSAssert.assertMainThread();

        return mItems.size();
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= mItems.size()) {
            return AdapterView.INVALID_ROW_ID;
        }

        Item item = getItem(position);
        return item.getAdapterItemId();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int pos, @Nullable View convertView, ViewGroup parent) {
        Item item = getItem(pos);
        if (convertView == null) {
            convertView = createView(parent, item);
        }

        if (item instanceof StandardMenuItem) {
            bindStandardItem(parent, convertView, (StandardMenuItem) item, pos);
        } else {
            TextView tv = convertView.findViewById(R.id.text1);
            if (tv != null) {
                tv.setText(item.getItemTitle());
            }

            int iconRid = item.getIconRid();
            ImageView iv = convertView.findViewById(R.id.icon);
            if (iv != null) {
                if (iconRid == 0) {
                    if (parent instanceof GridView) {
                        mThumbnailProcessor.setNoArtwork(iv);
                    } else {
                        iv.setVisibility(View.GONE);
                        iv.setImageResource(0);
                    }
                } else {
                    iv.setVisibility(View.VISIBLE);
                    Drawable d = ContextCompat.getDrawable(iv.getContext(), iconRid);
                    OSAssert.assertNotNull(d, "not null");
                    Drawable newDrawable = Drawables.getTintedDrawable(iv.getContext(), d);
                    iv.setImageDrawable(newDrawable);
                    iv.setContentDescription(mContext.getString(R.string.item_icon_desc));
                }
            }
        }

        return convertView;
    }

    protected ItemType getItemType(Item item) {
        return item.getBaseType();
    }

    @Override
    final public int getItemViewType(int position) {
        try {
            Item item = getItem(position);
            ItemType itemType = getItemType(item);
            return itemType.ordinal();
        } catch (IndexOutOfBoundsException e) {
            Reporting.report(e);
            return ItemType.IVT_TEXT.ordinal();
        }
    }

    @Override
    public int getViewTypeCount() {
        return ItemType.values().length;
    }

    @Override
    public int getPositionForSection(int section) {
        return getIndexer().getPositionForSection(section);
    }

    @Override
    public int getSectionForPosition(int position) {
        return getIndexer().getSectionForPosition(position);
    }

    @Override
    public Object[] getSections() {
        return getIndexer().getSections();
    }

    @Override
    public boolean isEnabled(int position) {
        Item item = getItem(position);
        return item.isEnabled();
    }

    @Nonnull
    protected SectionIndexer getIndexer() {
        OSAssert.assertMainThread();
        if (mIndexer == null) {
            if (isSorted()) {
                mIndexer = new SimpleAlphabetIndexer(StandardMenuItem.ALPHABETIC_SECTION_STRING);
            } else {
                mIndexer = new SimplePositionIndexer();
            }
        }
        return mIndexer;
    }

    @Override
    public void addPreload(int ndx) {
        OSAssert.assertMainThread();

        mPreloadItems.add(mItems.get(ndx));
    }

    @Override
    public void performPreloads(AbsListView listView) throws InterruptedException {
        OSAssert.assertNotMainThread();

        Item item;
        while ((item = mPreloadItems.poll()) != null) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            onPreloadItem(listView, item);
        }
    }

    protected void onPreloadItem(AbsListView listView, Item item) throws InterruptedException {
        item.preload(mThumbnailProcessor, listView);
    }

    @Override
    public void clearPreloads() {
        mPreloadItems.clear();
        mThumbnailProcessor.resetPreloads();
    }

    /**
     * allow subclasses to override the view type
     */
    @LayoutRes
    protected int getViewRid(ViewGroup parent, Item item) {
        if (parent instanceof GridView) {
            return getItemType(item).getGridLayoutRid();
        } else {
            return getItemType(item).getListLayoutRid();
        }
    }

    /**
     * allow subclasses to override the view creation routine
     */
    @Nonnull
    protected View createView(ViewGroup parent, Item item) {
        LayoutInflater inflater = LayoutInflater.from(mContext);

        @LayoutRes
        int rid = getViewRid(parent, item);

        View view = inflater.inflate(rid, parent, false);
        OSAssert.assertNotNull(view, "inflated view should be non-null");

        ViewHolder holder = new ViewHolder(view);
        view.setTag(R.id.tag_viewholder, holder);

        // reset view visibilities to expected defaults
//        if (holder.icon != null) {
//            if (parent instanceof GridView) {
//                ViewGroup.LayoutParams params = holder.icon.getLayoutParams();
//                if (params != null) {
//                    int width = SBPreferences.get().getGridThumbnailWidth();
//
//                    params.height = width;
//                    params.width = width;
//
//                    holder.icon.setLayoutParams(params);
//
//                    // relayout parent
//                    view.requestLayout();
//                }
//            }
//        }

        if (holder.actionButton != null) {
            holder.actionButton.setVisibility(View.GONE);

            // find the action button and create click listener that uses the
            // action button view tag to identify which Item
            // to use when the event is fired
            holder.actionButton.setOnClickListener(mActionButtonClickListener);

            // this allows focus to work properly when buttons are in the view
            if (view instanceof ViewGroup) {
                ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
        }
        if (holder.slider != null) {
            holder.slider.addOnChangeListener(mSliderChangeListener);
            holder.slider.addOnSliderTouchListener(mSliderTouchListener);

            // make viewholder available to the slider
            holder.slider.setTag(R.id.tag_viewholder, holder);
        }
        return view;
    }

    protected void bindText1(StandardMenuItem item, TextView text1) {
        text1.setText(item.getItemTitle());
    }

    protected void bindText2(StandardMenuItem item, TextView text2) {
        // no implementation by default
    }

    protected void bindText3(StandardMenuItem item, TextView text3) {
        // no implementation by default
    }

    @Nonnull
    protected IconVisibility bindIcon(StandardMenuItem item, ViewGroup parent, ImageView icon) {
        boolean loaded = false;

        // check cover id keys progressively for info
        IconRetriever iconRetriever = null;

        // this is called in a very high-volume situation, avoid creating an iterator
        ImmutableList<IconRetriever> irList = item.getIconRetrieverList();
        int irLength = irList.size();
        for (int i = 0; i < irLength; i++) {
            IconRetriever ir = irList.get(i);
            if (ir.applies(item)) {
                iconRetriever = ir;
                break;
            }
        }
        if (iconRetriever != null) {
            loaded = iconRetriever.load(mThumbnailProcessor, item, (AbsListView) parent, icon);
        }

        if (!loaded && parent instanceof GridView) {
            mThumbnailProcessor.setNoArtwork(icon);
            loaded = true;
        }
        return loaded ? IconVisibility.VISIBLE : IconVisibility.GONE;
    }

    protected boolean bindActionButton(StandardMenuItem item, View actionButton) {
        return false;
    }

    protected void bindCheckbox(StandardMenuItem item, CheckBox checkbox) {
        checkbox.setFocusable(false);
        checkbox.setFocusableInTouchMode(false);
        checkbox.setClickable(false);
        checkbox.setText(item.getItemTitle());
        checkbox.setChecked(item.getMenuElement().isCheckboxChecked());
    }

    protected void bindRadio(StandardMenuItem item, RadioButton radio) {
        radio.setFocusable(false);
        radio.setFocusableInTouchMode(false);
        radio.setClickable(false);
        radio.setText(item.getItemTitle());
        radio.setChecked(item.getMenuElement().isRadioChecked());
    }

    protected void bindSlider(StandardMenuItem item, Slider slider) {
        MenuElement elem = item.getMenuElement();
        slider.setValueFrom(elem.getSliderMinValue());
        if(elem.getSliderMaxValue() > elem.getSliderMinValue()) {
            // normal case
            slider.setValueTo(elem.getSliderMaxValue());
        } else {
            // bad data from remote, prevent a slider crash
            slider.setValueTo(elem.getSliderMinValue() + 1.0f);
        }

        float clampedValue = MoreMath.coerceIn(elem.getSliderInitialValue(), slider.getValueFrom(), slider.getValueTo());
        slider.setValue(clampedValue);
    }

    protected void bindStandardItem(ViewGroup parentView, View view, StandardMenuItem item, int position) {
        ViewHolder holder = (ViewHolder) view.getTag(R.id.tag_viewholder);
        item.prepare(parentView, holder, mThumbnailProcessor);

        if (holder.text1 != null) {
            mAutoSizeHelper.applyAutoSize(holder.text1, 1);
            bindText1(item, holder.text1);
        }
        if (holder.text2 != null) {
            mAutoSizeHelper.applyAutoSize(holder.text2, 1);
            bindText2(item, holder.text2);
        }
        if (holder.text3 != null) {
            bindText3(item, holder.text3);
        }

        if (holder.icon != null) {
            IconVisibility bound = bindIcon(item, parentView, holder.icon);
            //noinspection ResourceType
            holder.icon.setVisibility(bound.getVisibility());
        }
        if (holder.checkbox != null) {
            bindCheckbox(item, holder.checkbox);
        }
        if (holder.radio != null) {
            bindRadio(item, holder.radio);
        }
        if (holder.slider != null) {
            bindSlider(item, holder.slider);
            holder.slider.setTag(R.id.tag_clickableitem, new ClickableItemHolder(position, item));
        }
        if (holder.actionButton != null) {
            boolean visible = bindActionButton(item, holder.actionButton);
            int visibility = visible ? View.VISIBLE : View.GONE;
            if (visibility != holder.actionButton.getVisibility()) {
                holder.actionButton.setVisibility(visibility);
            }
            holder.actionButton.setTag(R.id.tag_clickableitem, new ClickableItemHolder(position, item));
        }
    }

    /**
     * class to pass action button clicks through to our custom listener
     */
    final private OnClickListener mActionButtonClickListener = v -> {
        ClickableItemHolder holder = (ClickableItemHolder) v.getTag(R.id.tag_clickableitem);
        BusProvider.getInstance().post(new ItemActionButtonClickEvent(v, holder.mItem, holder.mPosition));
    };

    final private Slider.OnChangeListener mSliderChangeListener = (slider, value, fromUser) -> {
        ViewHolder viewHolder = (ViewHolder) slider.getTag(R.id.tag_viewholder);
        if (viewHolder != null && viewHolder.text1 != null) {
            ClickableItemHolder holder = (ClickableItemHolder) slider.getTag(R.id.tag_clickableitem);
            if (holder != null) {
                holder.mItem.setMutatedSliderValue((int)value);
                bindText1(holder.mItem, viewHolder.text1);
            }
        }
    };

    final private Slider.OnSliderTouchListener mSliderTouchListener = new Slider.OnSliderTouchListener() {
        @Override
        public void onStartTrackingTouch(Slider slider) {
            // intentionally blank
        }

        @Override
        public void onStopTrackingTouch(Slider slider) {
            ClickableItemHolder holder = (ClickableItemHolder) slider.getTag(R.id.tag_clickableitem);
            holder.mItem.setMutatedSliderValue((int)slider.getValue());
            BusProvider.getInstance().post(new ItemSliderChangedEvent(slider, holder.mItem, (int)slider.getValue()));
        }
    };

    @Immutable
    static public class ViewHolder {

        protected ViewHolder(View container) {
            text1 = container.findViewById(R.id.text1);
            text2 = container.findViewById(R.id.text2);
            text3 = container.findViewById(R.id.text3);
            icon = container.findViewById(R.id.icon);
            actionButton = container.findViewById(R.id.action_button);
            checkbox = container.findViewById(R.id.checkbox);
            radio = container.findViewById(R.id.radio);
            slider = container.findViewById(R.id.slider);
        }

        @Nullable
        final public TextView text1;

        @Nullable
        final public TextView text2;

        @Nullable
        final public TextView text3;

        @Nullable
        final public ImageView icon;

        @Nullable
        final public View actionButton;

        @Nullable
        final public CheckBox checkbox;

        @Nullable
        final public RadioButton radio;

        @Nullable
        final public Slider slider;
    }

    static private class ClickableItemHolder {
        final int mPosition;
        final StandardMenuItem mItem;

        protected ClickableItemHolder(int position, StandardMenuItem item) {
            mPosition = position;
            mItem = item;
        }
    }

    class SimplePositionIndexer implements SectionIndexer {
        private Object[] mSectionArray;
        private int mSectionCount;
        private int mSectionSize;

        @Override
        public int getPositionForSection(int section) {
            calculateSectionInfo();

            return section * mSectionSize;
        }

        @Override
        public int getSectionForPosition(int position) {
            calculateSectionInfo();

            int retval = position / mSectionSize;
            if (retval < 0) {
                retval = 0;
            }
            if (retval >= mSectionCount) {
                retval = mSectionCount - 1;
            }
            return retval;
        }

        @Override
        public Object[] getSections() {
            calculateSectionInfo();
            return mSectionArray.clone();
        }

        private void calculateSectionInfo() {
            if (mSectionArray == null) {
                mSectionSize = getCount() / 50;
                mSectionSize = Math.max(mSectionSize, 10);
                while (mSectionSize % 10 != 0) {
                    mSectionSize++;
                }

                mSectionCount = getCount() / mSectionSize + 1;

                mSectionArray = new String[mSectionCount];
                for (int i = 0; i < mSectionCount; i++) {
                    mSectionArray[i] = Integer.toString(i * mSectionSize);
                }
            }
        }
    }

    class SimpleAlphabetIndexer implements SectionIndexer {
        /**
         * The string of characters that make up the indexing sections.
         */
        protected CharSequence mAlphabet;

        /**
         * Cached length of the alphabet array.
         */
        final private int mAlphabetLength;

        /**
         * This contains a cache of the computed indices so far. It will get reset whenever
         * the dataset changes or the cursor changes.
         */
        final private SparseIntArray mAlphaMap;

        /**
         * Use a collator to compare strings in a localized manner.
         */
        final private java.text.Collator mCollator;

        /**
         * The section array converted from the alphabet string.
         */
        final private String[] mAlphabetArray;

        /**
         * Constructs the indexer.
         *
         * @param alphabet string containing the alphabet, with space as the first character.
         *                 For example, use the string " ABCDEFGHIJKLMNOPQRSTUVWXYZ" for English indexing.
         *                 The characters must be uppercase and be sorted in ascii/unicode order. Basically
         *                 characters in the alphabet will show up as preview letters.
         */
        public SimpleAlphabetIndexer(CharSequence alphabet) {
            mAlphabet = alphabet;
            mAlphabetLength = alphabet.length();
            mAlphabetArray = new String[mAlphabetLength];
            for (int i = 0; i < mAlphabetLength; i++) {
                mAlphabetArray[i] = Character.toString(mAlphabet.charAt(i));
            }
            mAlphaMap = new SparseIntArray(mAlphabetLength);
            // Get a Collator for the current locale for string comparisons.
            mCollator = java.text.Collator.getInstance();
            mCollator.setStrength(java.text.Collator.PRIMARY);
        }

        /**
         * Returns the section array constructed from the alphabet provided in the constructor.
         *
         * @return the section array
         */
        @Override
        public Object[] getSections() {
            return mAlphabetArray;
        }

        /**
         * Default implementation compares the first character of word with letter.
         */
        protected int compare(String word, String letter) {
            final String firstLetter;
            if (word.length() == 0) {
                firstLetter = " ";
            } else {
                firstLetter = word.substring(0, 1);
            }

            return mCollator.compare(firstLetter, letter);
        }

        /**
         * Performs a binary search or cache lookup to find the first row that
         * matches a given section's starting letter.
         *
         * @param sectionIndex the section to search for
         * @return the row index of the first occurrence, or the nearest next letter.
         * For instance, if searching for "T" and no "T" is found, then the first
         * row starting with "U" or any higher letter is returned. If there is no
         * data following "T" at all, then the list size is returned.
         */
        @Override
        public int getPositionForSection(int sectionIndex) {
            final SparseIntArray alphaMap = mAlphaMap;
            if (mAlphabet == null) {
                return 0;
            }

            // Check bounds
            if (sectionIndex <= 0) {
                return 0;
            }
            if (sectionIndex >= mAlphabetLength) {
                sectionIndex = mAlphabetLength - 1;
            }

            int count = getCount();
            int start = 0;
            int end = count;
            int pos;

            char letter = mAlphabet.charAt(sectionIndex);
            String targetLetter = Character.toString(letter);
            //noinspection UnnecessaryLocalVariable
            int key = letter;
            // Check map
            if (Integer.MIN_VALUE != (pos = alphaMap.get(key, Integer.MIN_VALUE))) {
                // Is it approximate? Using negative value to indicate that it's
                // an approximation and positive value when it is the accurate
                // position.
                if (pos < 0) {
                    pos = -pos;
                    end = pos;
                } else {
                    // Not approximate, this is the confirmed start of section, return it
                    return pos;
                }
            }

            // Do we have the position of the previous section?
            if (sectionIndex > 0) {
                int prevLetter =
                        mAlphabet.charAt(sectionIndex - 1);
                int prevLetterPos = alphaMap.get(prevLetter, Integer.MIN_VALUE);
                if (prevLetterPos != Integer.MIN_VALUE) {
                    start = Math.abs(prevLetterPos);
                }
            }

            // Now that we have a possibly optimized start and end, let's binary search

            pos = (end + start) / 2;

            while (pos < end) {
                // Get letter at pos
                String curName = getStringAtPosition(pos);
                int diff = compare(curName, targetLetter);
                if (diff != 0) {
                    // TODO: Commenting out approximation code because it doesn't work for certain
                    // lists with custom comparators
                    // Enter approximation in hash if a better solution doesn't exist
                    // String startingLetter = Character.toString(getFirstLetter(curName));
                    // int startingLetterKey = startingLetter.charAt(0);
                    // int curPos = alphaMap.get(startingLetterKey, Integer.MIN_VALUE);
                    // if (curPos == Integer.MIN_VALUE || Math.abs(curPos) > pos) {
                    //     Negative pos indicates that it is an approximation
                    //     alphaMap.put(startingLetterKey, -pos);
                    // }
                    // if (mCollator.compare(startingLetter, targetLetter) < 0) {
                    if (diff < 0) {
                        start = pos + 1;
                        if (start >= count) {
                            pos = count;
                            break;
                        }
                    } else {
                        end = pos;
                    }
                } else {
                    // They're the same, but that doesn't mean it's the start
                    if (start == pos) {
                        // This is it
                        break;
                    } else {
                        // Need to go further lower to find the starting row
                        end = pos;
                    }
                }
                pos = (start + end) / 2;
            }
            alphaMap.put(key, pos);
            return pos;
        }

        /**
         * Returns the section index for a given position in the list by querying the item
         * and comparing it with all items in the section array.
         */
        @Override
        public int getSectionForPosition(int position) {
            String curName = getStringAtPosition(position);
            // Linear search, as there are only a few items in the section index
            // Could speed this up later if it actually gets used.
            for (int i = 0; i < mAlphabetLength; i++) {
                char letter = mAlphabet.charAt(i);
                String targetLetter = Character.toString(letter);
                if (compare(curName, targetLetter) == 0) {
                    return i;
                }
            }
            return 0; // Don't recognize the letter - falls under zero'th section
        }

        @Nonnull
        private String getStringAtPosition(int position) {
            Item item = getItem(position);

            String sectionName = item.getSectionName();
            return Strings.nullToEmpty(sectionName);
        }

    }
}
