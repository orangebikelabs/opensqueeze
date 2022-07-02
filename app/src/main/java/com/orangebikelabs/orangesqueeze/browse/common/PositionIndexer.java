/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import android.database.Cursor;
import android.database.DataSetObserver;
import android.widget.SectionIndexer;

/**
 * A section indexer that provides simple numeric access to every 50 or so items as a section.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class PositionIndexer extends DataSetObserver implements SectionIndexer {
    final private Cursor mCursor;

    private Object[] mSectionArray;
    private int mSectionCount;
    private int mSectionSize;

    public PositionIndexer(Cursor cursor) {
        mCursor = cursor;
        mCursor.registerDataSetObserver(this);
    }

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
            mSectionSize = mCursor.getCount() / 50;
            mSectionSize = Math.max(mSectionSize, 10);
            while (mSectionSize % 10 != 0) {
                mSectionSize++;
            }

            mSectionCount = mCursor.getCount() / mSectionSize + 1;

            mSectionArray = new String[mSectionCount];
            for (int i = 0; i < mSectionCount; i++) {
                mSectionArray[i] = Integer.toString(i * mSectionSize);
            }
        }
    }

    @Override
    public void onChanged() {
        super.onChanged();

        mSectionArray = null;
    }
}
