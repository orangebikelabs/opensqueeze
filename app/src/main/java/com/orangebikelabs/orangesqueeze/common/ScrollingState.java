/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.os.SystemClock;
import android.widget.AbsListView;

import com.google.common.util.concurrent.Monitor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

/**
 * @author tsandee
 */
public class ScrollingState {
    final private static AtomicLong sScrollingState = new AtomicLong(0);
    final private static long GRACE = 5000L;

    public static void monitorScrolling(AbsListView view) {
        view.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // update a global variable indicating if we're scrolling or not
                setScrolling(scrollState != SCROLL_STATE_IDLE);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                bumpIfScrolling();
            }
        });
    }

    public static void monitorScrolling(AbsListView view, final @Nullable AbsListView.OnScrollListener listener) {
        view.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // update a global variable indicating if we're scrolling or not
                setScrolling(scrollState != SCROLL_STATE_IDLE);

                if (listener != null) {
                    listener.onScrollStateChanged(view, scrollState);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                bumpIfScrolling();

                if (listener != null) {
                    listener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                }

            }
        });
    }

    private static void bumpIfScrolling() {
        long diff = getScrollAutoEnd();
        if (diff > 0 && diff < GRACE - 1000) {
            setScrolling(true);
        }
    }

    public static boolean isScrolling() {
        long diff = getScrollAutoEnd();
        return diff > 0;
    }

    private static long getScrollAutoEnd() {
        return sScrollingState.get() - SystemClock.uptimeMillis();
    }

    final static private Monitor sScrollingMonitor = new Monitor();
    final static private Monitor.Guard sNotScrollingGuard = new Monitor.Guard(sScrollingMonitor) {
        @Override
        public boolean isSatisfied() {
            return !isScrolling();
        }
    };

    public static void setScrolling(boolean value) {
        sScrollingMonitor.enter();
        try {
            if (value) {
                // set a 5 second buffer after scrolling stops, scroll state automatically resets
                sScrollingState.set(SystemClock.uptimeMillis() + GRACE);
            } else {
                sScrollingState.set(0L);
            }
        } finally {
            sScrollingMonitor.leave();
        }
    }

    /**
     * returns true if we exited in a non-scrolling state
     */
    public static boolean waitForNotScrolling(long time, TimeUnit units) throws InterruptedException {
        if (!isScrolling()) {
            return true;
        }

        boolean entered = sScrollingMonitor.enterWhen(sNotScrollingGuard, time, units);
        if (entered) {
            sScrollingMonitor.leave();

            // always append GRACE on to the end
            Thread.sleep(GRACE);
        }
        return entered;
    }
}
