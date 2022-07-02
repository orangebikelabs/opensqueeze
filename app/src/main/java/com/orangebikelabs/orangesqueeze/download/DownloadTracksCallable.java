/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequest;
import com.orangebikelabs.orangesqueeze.menu.ActionNames;
import com.orangebikelabs.orangesqueeze.menu.MenuAction;
import com.orangebikelabs.orangesqueeze.menu.MenuHelpers;
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;

/**
 * @author tsandee
 */
public class DownloadTracksCallable implements Callable<DownloadJob> {

    @Nonnull
    final DownloadTracksLoaderState mState;

    DownloadTracksCallable(DownloadTracksLoaderState state) {
        mState = state;
    }

    @Nonnull
    @Override
    public DownloadJob call() {

        if (mState.takeInitialRequestFlag()) {
            executeInitialRequest();
        }

        // are we done building list of tracks
        boolean discoveryComplete = mState.isDiscoveryComplete();

        DownloadJob job = new DownloadJob();
        int size = job.addTracks(mState.getReadyTrackList());

        if (discoveryComplete) {
            job.setProgress(size, mState.getTotalTrackCount());
        } else {
            job.setProgress(0, 100);
        }
        return job;
    }

    public void cancel() {
        mState.cleanup();
    }

    /**
     * perform the initial request to get a list of tracks. This will be followed up by nested tasks as new potential folders are found.
     */
    private void executeInitialRequest() {
        executeNestedRequest(mState.getCommands(), mState.getParameters());
    }

    /**
     * perform task to catalog tracks at a folder based on the folder ID
     */
    protected void executeNestedRequest(final List<String> commands, List<String> params) {
        if (!mState.addVisitedNode(commands, params)) {
            // already exists, don't recurse again
            return;
        }

        // increment the outstanding tasks flag so that we know when we're done inspecting the hierarchy
        mState.getOutstandingDiscoveryTasks().incrementAndGet();

        BrowseRequest request = new BrowseRequest(mState.getPlayerId()) {

            @Override
            protected void finalizeRequest() {
                boolean changed = false;

                for (StandardMenuItem item : Iterables.filter(mItemList, StandardMenuItem.class)) {
                    if (processItem(item)) {
                        changed = true;
                    }
                }
                if (mState.getOutstandingDiscoveryTasks().decrementAndGet() == 0 || changed) {
                    mState.notifyObservers();
                }
            }

            /** returns whether a change was made to the data structure */
            private boolean processItem(StandardMenuItem item) {
                boolean changed = false;
                String type = item.getNode().path("type").asText();

                if (type.equals("playlist") || type.equals("album")) {
                    MenuAction goAction = MenuHelpers.getAction(item.getMenuElement(), ActionNames.GO);
                    if (goAction != null && DownloadHelper.isNestableRequest(goAction.getCommands())) {
                        List<String> params = MenuHelpers.buildParametersAsList(item.getMenuElement(), goAction, false);
                        if(params != null) {
                            executeNestedRequest(goAction.getCommands(), params);
                        }
                    }
                } else if (item.getMenuElement().isTrack()) {
                    String trackId = item.getMenuElement().getTrackId();
                    if (trackId != null) {
                        DownloadTrack elem = new DownloadTrack(trackId, item.getItemTitle());
                        if (mState.addDownloadElement(elem)) {
                            changed = true;
                        }
                    }
                } else {
                    String id = item.getNode().path("id").asText();
                    if (!Strings.isNullOrEmpty(id)) {
                        if (type.equals("folder")) {
                            executeNestedRequest(ImmutableList.of("musicfolder"), ImmutableList.of("folder_id:" + id));
                        } else if (type.equals("track")) {
                            DownloadTrack elem = new DownloadTrack(id, item.getNode().path("filename").asText());
                            if (mState.addDownloadElement(elem)) {
                                changed = true;
                            }
                        } else if (commands.equals(Collections.singletonList("titles"))) {
                            DownloadTrack elem = new DownloadTrack(id, item.getNode().path("title").asText());
                            if (mState.addDownloadElement(elem)) {
                                changed = true;
                            }
                        }
                    }

                }
                return changed;
            }
        };
        request.setCacheable(true);
        request.setCommands(commands);
        request.setParameters(params);
        request.setLoopAndCountKeys(Arrays.asList("folder_loop", "item_loop", "titles_loop"), null);
        request.submit(mState.getExecutor());
    }
}
