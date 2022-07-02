/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.artwork;

import android.os.Bundle;
import android.os.Handler;

import androidx.fragment.app.Fragment;

import android.os.Looper;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Adapter;
import android.widget.HeaderViewListAdapter;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.InterruptedAwareRunnable;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.ScrollingState;
import com.orangebikelabs.orangesqueeze.common.event.ConnectionStateChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.TriggerListPreload;
import com.squareup.otto.Subscribe;

import java.util.concurrent.Future;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Based on direction user is scrolling, choose ideal preload targets. Interrupt preload when scrolling occurs.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class ListPreloadFragment extends Fragment {

    /**
     * use this thread when performing initial preload operations
     */
    static private final ListeningScheduledExecutorService sPreloadThread = OSExecutors.newSingleThreadScheduledExecutor("Preloads");

    /**
     * interface to implement in adapter to allow preload
     */
    public interface PreloadAdapter extends Adapter {
        void addPreload(int ndx);

        void performPreloads(AbsListView listView) throws InterruptedException;

        void clearPreloads();
    }

    final static private float PRELOAD_FACTOR = 0.5f; //preload 1/2 a page
    final static private int PRELOAD_DELAY = 500;
    final static private String LISTVIEWID_KEYS = "listViewIds";

    final private static Handler mHandler = new Handler(Looper.getMainLooper());
    protected boolean mPreloadEnabled = true;
    protected boolean mServerConnected;

    private int[] mListViewIds;

    @GuardedBy("this")
    private AbsListView mInitListView;

    protected int mFirstVisibleItem, mVisibleCount;
    protected boolean mScrollingForward;

    // accessed from main thread only
    protected Future<?> mPreloadTask;

    @Nonnull
    static public ListPreloadFragment newInstance() {
        return newInstance(R.id.browseview);
    }

    @Nonnull
    static public ListPreloadFragment newInstance(int... ids) {

        ListPreloadFragment retval = new ListPreloadFragment();

        Bundle args = new Bundle();
        args.putIntArray(LISTVIEWID_KEYS, ids);

        retval.setArguments(args);
        return retval;
    }

    public ListPreloadFragment() {
    }

    @Override
    public void onCreate(@androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mListViewIds = requireArguments().getIntArray(LISTVIEWID_KEYS);
    }

    @Nullable
    protected AbsListView getListView() {
        AbsListView retval = null;
        if (isAdded()) {
            for (int id : mListViewIds) {
                View lv = requireActivity().findViewById(id);
                if (lv instanceof AbsListView) {
                    retval = (AbsListView) lv;
                    break;
                }
            }
            if (retval != null) {
                synchronized (this) {
                    if (mInitListView != retval) {
                        mInitListView = retval;

                        ScrollingState.monitorScrolling(mInitListView, mListScrollListener);
                    }
                }
            }
            if (retval == null) {
                OSLog.w("List preload fragment NOT associated with a listview");
            }
        }
        return retval;
    }

    protected void setEnabled(boolean enabled) {
        OSAssert.assertMainThread();

        PreloadAdapter adapter = getAdapter();
        if (adapter == null) {
            mPreloadEnabled = false;
            return;
        }

        if (mPreloadEnabled != enabled) {
            mPreloadEnabled = enabled;

            if (mPreloadEnabled) {
                triggerPreload();
            } else {
                mHandler.removeCallbacks(mBuildPreloadRunnable);
                // if we're in a fling, cancel any existing preloads until the fling stops
                if (mPreloadTask != null) {
                    mPreloadTask.cancel(true);
                    mPreloadTask = null;
                }
                adapter.clearPreloads();
            }
        }
    }

    public void triggerPreload() {
        // remove any scheduled preloads
        mHandler.removeCallbacks(mBuildPreloadRunnable);
        if (!isResumed()) {
            return;
        }

        PreloadAdapter adapter = getAdapter();
        if (adapter == null || adapter.getCount() == 0) {
            return;
        }

        // delay for a bit, then do preload
        mHandler.postDelayed(mBuildPreloadRunnable, PRELOAD_DELAY);
    }

    @Override
    public void onPause() {
        super.onPause();

        mHandler.removeCallbacks(mBuildPreloadRunnable);

        try {
            BusProvider.getInstance().unregister(mEventReceiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mServerConnected = SBContextProvider.get().isConnected();
        triggerPreload();

        try {
            BusProvider.getInstance().register(mEventReceiver);
        } catch (IllegalArgumentException e) {
            // ignore
        }
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void onTriggerListPreload(TriggerListPreload event) {
            triggerPreload();
        }

        @Subscribe
        public void onConnectivityStateChange(ConnectionStateChangedEvent event) {
            mServerConnected = event.getConnectionInfo().isConnected();
        }
    };

    @Nullable
    synchronized PreloadAdapter getAdapter() {
        PreloadAdapter retval = null;
        AbsListView lv = getListView();
        if (lv != null) {
            Adapter adapter = lv.getAdapter();
            //noinspection ChainOfInstanceofChecks
            if (adapter instanceof HeaderViewListAdapter) {
                adapter = ((HeaderViewListAdapter) adapter).getWrappedAdapter();
            }
            if (adapter instanceof PreloadAdapter) {
                retval = (PreloadAdapter) adapter;
            }
        }
        return retval;
    }

    final private OnScrollListener mListScrollListener = new OnScrollListener() {

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            int lastFirstVisible = mFirstVisibleItem;

            mFirstVisibleItem = firstVisibleItem;
            mVisibleCount = visibleItemCount;

            mScrollingForward = (mFirstVisibleItem > lastFirstVisible);

            if (mPreloadEnabled && lastFirstVisible != mFirstVisibleItem) {
                triggerPreload();
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            switch (scrollState) {
                case SCROLL_STATE_FLING:
                    setEnabled(false);
                    break;
                case SCROLL_STATE_IDLE:
                case SCROLL_STATE_TOUCH_SCROLL:
                    setEnabled(true);
                    break;
                default:
                    // other unsupported value
                    OSAssert.assertNotReach();
                    break;
            }
        }
    };

    /**
     * runs on the main thread, itemizes things to preload and adds them to the top of the thumbnail handler
     */
    final private Runnable mBuildPreloadRunnable = new Runnable() {
        @Override
        public void run() {
            OSAssert.assertMainThread();
            final AbsListView listView = getListView();
            final PreloadAdapter adapter = getAdapter();
            if (adapter == null || listView == null || !mServerConnected) {
                return;
            }

            if (mPreloadTask != null && !mPreloadTask.isDone()) {
                // preload is running
                return;
            }

            OSLog.TimingLoggerCompat timing = OSLog.Tag.TIMING.newTimingLogger("build preload timing");
            adapter.clearPreloads();
            // build preload list in adapter
            if (mScrollingForward) {
                preloadAfter(adapter);
                preloadBefore(adapter);
            } else {
                preloadBefore(adapter);
                preloadAfter(adapter);
            }
            mPreloadTask = sPreloadThread.submit(new InterruptedAwareRunnable() {
                @Override
                protected void doRun() throws InterruptedException {
                    OSLog.TimingLoggerCompat timing = OSLog.Tag.TIMING.newTimingLogger("perform preload timing");
                    adapter.performPreloads(listView);
                    timing.close();
                }
            });
            timing.close();
        }

        private void preloadAfter(PreloadAdapter adapter) {
            if (adapter.isEmpty()) {
                return;
            }


            int afterStart = Math.min(adapter.getCount() - 1, mFirstVisibleItem + mVisibleCount + 1);
            int afterEnd = Math.min(adapter.getCount() - 1, afterStart + ((int) (mVisibleCount * PRELOAD_FACTOR)));

            for (int i = afterStart; i <= afterEnd; i++) {
                adapter.addPreload(i);
            }
        }

        protected void preloadBefore(PreloadAdapter adapter) {
            if (adapter.isEmpty()) {
                return;
            }

            int beforeStart = mFirstVisibleItem - 1;
            int beforeEnd = Math.max(-1, mFirstVisibleItem - ((int) (mVisibleCount * PRELOAD_FACTOR)));

            for (int i = beforeStart; i > beforeEnd; i--) {
                adapter.addPreload(i);
            }
        }
    };
}
