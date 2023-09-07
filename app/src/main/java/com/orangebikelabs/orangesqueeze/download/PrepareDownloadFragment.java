/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.SBFragment;
import com.orangebikelabs.orangesqueeze.common.MenuTools;
import com.orangebikelabs.orangesqueeze.common.NavigationCommandSet;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.event.AppPreferenceChangeEvent;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.ui.TrackDownloadPreferenceActivity;
import com.squareup.otto.Subscribe;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * @author tsandee
 */
public class PrepareDownloadFragment extends SBFragment {
    @Nonnull
    static public PrepareDownloadFragment newInstance(NavigationItem item) {
        PrepareDownloadFragment retval = new PrepareDownloadFragment();
        Bundle args = new Bundle();
        NavigationItem.Companion.putNavigationItem(args, item);
        retval.setArguments(args);
        return retval;
    }

    protected List<String> mCommands;
    protected List<String> mParams;
    protected String mDownloadTitle;
    protected PlayerId mPlayerId;
    protected int mDownloadableCount;
    protected ProgressBar mProgress;

    protected ListView mListView;
    protected DownloadTrackAdapter mAdapter;

    @Nullable
    private Disposable mPrepareDownloadDisposable;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NavigationItem item = OSAssert.assertNotNull(NavigationItem.Companion.getNavigationItem(getArguments()), "Can't be null");
        NavigationCommandSet ncs = item.getRequestCommandSet();
        OSAssert.assertNotNull(ncs, "can't be null");

        mCommands = ncs.getCommands();
        mParams = ncs.getParameters();
        mPlayerId = mSbContext.getPlayerId();
        mDownloadTitle = item.getName();

        mBus.register(mEventReceiver);

        requireActivity().addMenuProvider(mMenuProvider, this, Lifecycle.State.RESUMED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBus.unregister(mEventReceiver);
    }

    protected void refreshOptionsMenu() {
        FragmentActivity activity = getActivity();
        if (activity != null && isAdded() && !activity.isFinishing()) {
            activity.invalidateMenu();
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mProgress = view.findViewById(R.id.listload_progress);

        mListView = view.findViewById(android.R.id.list);
        mListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);

        mAdapter = new DownloadTrackAdapter(requireContext());
        mListView.setAdapter(mAdapter);

        updateDownloadButtons();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.downloads_prepare, container, false);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mPlayerId != null) {
            LoaderManager.getInstance(this).initLoader(0, getArguments(), mLoaderCallbacks);
        }
    }

    protected void updateDownloadButtons() {
        int downloadableCount = 0;
        for (int i = 0; i < mAdapter.getCount(); i++) {
            DownloadTrack elem = mAdapter.getItem(i);
            if (elem.isSelected() && elem.isDownloadable()) {
                downloadableCount++;
            }
        }
        mDownloadableCount = downloadableCount;
        refreshOptionsMenu();
    }

    @SuppressLint("StaticFieldLeak")
    private void prepareDownloads() {
        if (mPrepareDownloadDisposable != null) {
            return;
        }

        refreshOptionsMenu();
        String batchName = getBatchName();
        ArrayList<DownloadTrack> downloadItems = new ArrayList<>();
        for (int i = 0; i < mAdapter.getCount(); i++) {
            DownloadTrack elem = mAdapter.getItem(i);
            if (elem.isSelected()) {
                downloadItems.add(elem);
            }
        }

        Context applicationContext = mSbContext.getApplicationContext();
        mPrepareDownloadDisposable = Completable
                .fromAction(() -> {
                    for (DownloadTrack dt : downloadItems) {
                        dt.refreshPreferences();

                        Uri sourceUri = dt.getSourceUri();

                        File newFile = dt.getDestinationFile();

                        addDownload(applicationContext, batchName, dt.getId(), sourceUri, newFile, dt.toString(), dt.getExpectedLength(), dt.isTranscoded());
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnDispose(() -> {
                    mPrepareDownloadDisposable = null;
                })
                .subscribe(() -> {
                    applicationContext.startService(DownloadService.getStartDownloadsIntent(applicationContext, mSbContext.getServerId()));

                    Activity activity = getActivity();
                    if (activity == null) return;

                    Snackbar.make(requireView(), R.string.download_started, Snackbar.LENGTH_SHORT)
                            .show();
                    activity.finish();
                }, (throwable -> {
                    Activity activity = getActivity();
                    if (activity == null) return;

                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
                    builder.setTitle(R.string.error_download_title)
                            .setMessage(throwable.getMessage())
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(R.string.ok, (dlg, which) -> {
                            })
                            .show();
                }));

    }

    private void addDownload(Context context, String batch, String trackId, Uri sourceUri, File targetFile, String title, @Nullable Long contentLength, boolean transcoded) {
        DownloadStatus status = new DownloadStatus();
        if (contentLength != null) {
            status.setContentLength(contentLength);
        }

        DatabaseAccess.getInstance(context)
                .getDownloadQueries()
                .insert(batch, trackId, transcoded, System.currentTimeMillis(), title,
                        sourceUri.toString(), targetFile.getAbsolutePath(),
                        mSbContext.getServerId(), status);
    }

    private String getBatchName() {
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        return getString(R.string.download_batch_name, mDownloadTitle, formatter.format(new Date()));
    }

//	private void prepareDownloads() {
//		try {
//			SBCredentials creds = mSbContext.getConnectionCredentials().orElse(null);
//
//			DownloadManager downloadManager = (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
//			for (int i = 0; i < mAdapter.getCount(); i++) {
//				DownloadTrack elem = mAdapter.getItem(i);
//				if (!elem.isSelected()) {
//					continue;
//				}
//
//				Uri sourceUri = elem.getSourceUri();
//
//				DownloadManager.Request request = new DownloadManager.Request(sourceUri);
//				if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
//					Honeycomb.allowScanningByMediaScanner(request);
//				}
//				if (creds != null) {
//					URI uri = URI.create(sourceUri.toString());
//					Header authHeader = creds.getHeader(uri).orElse(null);
//					if (authHeader != null) {
//						request.addRequestHeader(authHeader.getName(), authHeader.getValue());
//					}
//				}
//				File newFile = elem.getDestinationFile();
//				if (newFile == null) {
//					continue;
//				}
//
//				File parentDir = newFile.getParentFile();
//				if (!parentDir.isDirectory()) {
//					FileUtils.mkdirsChecked(parentDir);
//				}
//
//				if (newFile.isFile()) {
//					// right now we overwrite everything
//					FileUtils.deleteChecked(newFile);
//				}
//
//				request.setDestinationUri(Uri.fromFile(newFile));
//				request.setTitle(elem.toString());
//
//				downloadManager.enqueue(request);
//
//				getActivity().finish();
//			}
//		} catch (Exception e) {
//			AlertDialogFragment fragment = AlertDialogFragment.newInstance(R.string.error_download_title, e.getMessage());
//			fragment.show(getFragmentManager(), null);
//		}
//	}

    final private MenuProvider mMenuProvider = new MenuProvider() {

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            MenuItem item = menu.findItem(R.id.menu_search);
            if (item != null) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }

            MenuTools.setVisible(menu, R.id.menu_players, false);
            MenuTools.setEnabled(menu, R.id.menu_startdownload, mDownloadableCount > 0 && mPrepareDownloadDisposable == null);
        }

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.preparedownloads, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menu_downloadpreferences) {
                startActivity(new Intent(getContext(), TrackDownloadPreferenceActivity.class));
                return true;
            } else if (menuItem.getItemId() == R.id.menu_startdownload) {
                prepareDownloads();
                return true;
            } else {
                return false;
            }
        }
    };

    final private LoaderManager.LoaderCallbacks<DownloadJob> mLoaderCallbacks = new LoaderManager.LoaderCallbacks<>() {
        @Override
        @Nonnull
        public Loader<DownloadJob> onCreateLoader(int id, @Nullable Bundle args) {
            return new DownloadTracksLoader(requireContext(), mCommands, mParams, mPlayerId);
        }

        @Override
        public void onLoadFinished(Loader<DownloadJob> loader, DownloadJob job) {
            mAdapter.setNotifyOnChange(false);
            mAdapter.clear();
            for (DownloadTrack e : job) {
                mAdapter.add(e);
            }
            mAdapter.notifyDataSetChanged();

            if (job.isLoadComplete()) {
                mProgress.setMax(1);
                mProgress.setProgress(1);
            } else {
                mProgress.setMax(job.getMax());
                mProgress.setProgress(job.getPosition());
            }

            updateDownloadButtons();
        }

        @Override
        public void onLoaderReset(Loader<DownloadJob> loader) {
            // nothing
        }
    };

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenAppPreferenceChanges(AppPreferenceChangeEvent event) {
            if (SBPreferences.get().getDownloadResultsKeys().contains(event.getKey())) {
                OSExecutors.getMainThreadExecutor().execute(() -> {
                    if (mAdapter == null) {
                        return;
                    }

                    // cache changes, make sure everything is updated in terms of download location
                    for (int i = 0; i < mAdapter.getCount(); i++) {
                        DownloadTrack track = mAdapter.getItem(i);
                        track.refreshPreferences();
                    }
                    mAdapter.notifyDataSetChanged();
                });
            }
        }
    };

    class DownloadTrackAdapter extends ArrayAdapter<DownloadTrack> {
        DownloadTrackAdapter(Context context) {
            super(context, R.layout.downloaditem, R.id.checkbox);
        }

        @Nonnull
        @Override
        public DownloadTrack getItem(int position) {
            return super.getItem(position);
        }

        @Override
        @Nonnull
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            OSAssert.assertNotNull(view, "view should not be null");

            ViewHolder holder = (ViewHolder) view.getTag(R.id.tag_viewholder);
            if (holder == null) {
                holder = new ViewHolder(this, view);
                view.setTag(R.id.tag_viewholder, holder);
            }
            holder.setPosition(position);
            return view;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            DownloadTrack track = getItem(position);
            return track.getAdapterId();
        }
    }

    class ViewHolder {
        final DownloadTrackAdapter mAdapter;
        final CheckBox mCheckbox;

        DownloadTrack mElement;

        public ViewHolder(DownloadTrackAdapter adapter, View view) {
            mAdapter = adapter;
            mCheckbox = view.findViewById(R.id.checkbox);
            mCheckbox.setOnCheckedChangeListener((checkbox, isChecked) -> {
                mElement.setSelected(isChecked);
                updateDownloadButtons();
            });
        }

        void setPosition(int pos) {
            mElement = mAdapter.getItem(pos);
            mCheckbox.setChecked(mElement.isSelected());
        }
    }
}
