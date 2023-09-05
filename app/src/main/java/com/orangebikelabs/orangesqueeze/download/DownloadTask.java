/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.util.DisplayMetrics;

import com.beaglebuddy.mp3.MP3;
import com.beaglebuddy.mp3.enums.Encoding;
import com.beaglebuddy.mp3.enums.FrameType;
import com.beaglebuddy.mp3.enums.PictureType;
import com.beaglebuddy.mp3.id3v23.ID3v23Frame;
import com.beaglebuddy.mp3.id3v23.frame_body.ID3v23FrameBodyTextInformation;
import com.google.common.base.Strings;
import com.google.common.io.ByteSink;
import com.google.common.io.Files;
import com.orangebikelabs.orangesqueeze.artwork.ArtworkType;
import com.orangebikelabs.orangesqueeze.artwork.StandardArtworkCacheRequest;
import com.orangebikelabs.orangesqueeze.cache.CacheFuture;
import com.orangebikelabs.orangesqueeze.common.FileUtils;
import com.orangebikelabs.orangesqueeze.common.MoreOption;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.TrackInfo;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.database.Download;
import com.orangebikelabs.orangesqueeze.net.HttpUtils;
import com.orangebikelabs.orangesqueeze.net.SBCredentials;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * simple task that performs a single download
 *
 * @author tsandee
 */
class DownloadTask implements Callable<Boolean> {
    @Nonnull
    static public File getDownloadTempDirectory(Context context) {
        File baseDir = context.getFilesDir();
        File downloads = new File(baseDir, "OpenSqueezeDownloads");
        FileUtils.mkdirsUnchecked(downloads);
        return downloads;
    }

    final protected long mId;

    @Nullable
    final protected SBCredentials mCredentials;

    DownloadTask(long downloadId, @Nullable SBCredentials credentials) {
        mId = downloadId;
        mCredentials = credentials;
    }

    @Nonnull
    private Context getContext() {
        return SBContextProvider.get().getApplicationContext();
    }

    @Override
    public Boolean call() {
        Download download = DatabaseAccess.getInstance(getContext())
                .getDownloadQueries()
                .lookupWithId(mId).executeAsOneOrNull();
        if (download == null) {
            return Boolean.FALSE;
        }
        try {
            String source = download.getDownloadsource();
            File dest = new File(download.getDownloaddestination());
            String title = download.getDownloadtitle();
            DownloadStatus status = download.getDownloadstatus();
            String trackId = download.getDownloadtrackid();

            if (download(trackId, new URL(source), dest, title, status)) {
                // download successful
                scan(dest);
            }
            return Boolean.TRUE;
        } catch (MalformedURLException e) {
            Reporting.report(e);
            return Boolean.FALSE;
        }
    }

    private void scan(File destFile) {
        ScanClientProxy proxy = new ScanClientProxy(destFile);
        MediaScannerConnection connection = new MediaScannerConnection(getContext(), proxy);
        proxy.initConnection(connection);
        connection.connect();
    }

    private void writeMetadata(File file, File folder, TrackInfo ti) {
        File temporaryArtworkFile = null;
        try {
            MP3 mp3 = new MP3(file);

            if (mp3.getPictures().isEmpty()) {
                String coverId = ti.getCoverId().orNull();
                if (coverId != null) {
                    try {
                        DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
                        int screenWidth = Math.min(dm.heightPixels, dm.widthPixels);

                        StandardArtworkCacheRequest request = new StandardArtworkCacheRequest(getContext(), coverId, ArtworkType.ALBUM_THUMBNAIL, screenWidth);
                        URL url = request.getUrl();


                        temporaryArtworkFile = File.createTempFile("download_artwork", ".jpg", getContext().getFilesDir());
                        HttpURLConnection conn = HttpUtils.open(url, true);
                        if (mCredentials != null) {
                            mCredentials.apply(conn);
                        }

                        try(InputStream is = conn.getInputStream()) {
                            Files.asByteSink(temporaryArtworkFile).writeFrom(is);
                        }
                        mp3.setPicture(PictureType.FRONT_COVER, temporaryArtworkFile);
                    } catch (IOException e) {
                        OSLog.w("Error while downloading track artwork: " + e.getMessage(), e);
                    }
                }
            }

            mp3.setAlbum(ti.getTrackAlbum());
            mp3.setTitle(ti.getTrackName());

            double duration = MoreOption.getOrElse(ti.getDuration(), 0.0f);
            if (duration >= 0.0f && duration < 10000.0f) {
                mp3.setAudioDuration((int) duration);
            }

            String trackNumber = ti.getTrackNumber().orNull();
            if (trackNumber != null) {
                try {
                    mp3.setTrack(Integer.parseInt(trackNumber));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            // write part of set (disc/count) tag to mp3
            String discNumber = ti.getDiscNumber().orNull();
            if (discNumber != null) {
                String discCount = ti.getDiscCount().orNull();

                // if total disc count is known, include it
                String partOfSet;
                if (discCount != null) {
                    partOfSet = discNumber + "/" + discCount;
                } else {
                    partOfSet = discNumber;
                }

                ID3v23Frame frame = mp3.addFrame(FrameType.PART_OF_A_SET);
                ID3v23FrameBodyTextInformation body = (ID3v23FrameBodyTextInformation) frame.getBody();
                body.setText(partOfSet);
                body.setEncoding(Encoding.UTF_16);
            }
            String trackYear = ti.getYear().orNull();
            if (trackYear != null) {
                try {
                    mp3.setYear(Integer.parseInt(trackYear));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            String albumArtist = Strings.emptyToNull(ti.getAlbumArtist());
            if (albumArtist != null) {
                mp3.setBand(albumArtist);
            }
            String trackArtist = Strings.emptyToNull(ti.getTrackArtist());
            if (trackArtist != null) {
                ID3v23Frame frame = mp3.addFrame(FrameType.LEAD_PERFORMER);
                ID3v23FrameBodyTextInformation body = (ID3v23FrameBodyTextInformation) frame.getBody();
                body.setText(trackArtist);
                body.setEncoding(Encoding.UTF_16);
            }

            String genre = ti.getGenre().orNull();
            if (genre != null) {
                mp3.setMusicType(genre);
            }

            String comments = ti.getComments().orNull();
            if (comments != null) {
                mp3.setComments(comments);
            }
            mp3.save();

            // try to make folder.jpg
            if (temporaryArtworkFile != null) {
                String ext = Files.getFileExtension(temporaryArtworkFile.getPath());
                File potentialArtwork = new File(folder, "Folder." + ext);
                if (!potentialArtwork.exists()) {
                    Files.copy(temporaryArtworkFile, potentialArtwork);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            OSLog.w("Error while saving track metadata: " + e.getMessage(), e);
        } finally {
            if (temporaryArtworkFile != null) {
                FileUtils.deleteChecked(temporaryArtworkFile);
            }
        }
    }

    private boolean download(String trackId, URL source, File destFile, String title, DownloadStatus status) {
        File temporaryDownloadLocation = getTemporaryDownloadLocation();

        CacheFuture<TrackInfo> trackInfo = TrackInfo.load(SBContextProvider.get().getServerId(), trackId, OSExecutors.getUnboundedPool());

        try {
            status.markActive();
            status.update(mId);

            HttpURLConnection connection = HttpUtils.open(source, true);
            if (mCredentials != null) {
                mCredentials.apply(connection);
            }
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                OSLog.w("Error downloading file (reason=" + connection.getResponseMessage() + ")");
                status.markFailed(connection.getResponseMessage(), false);
                status.update(mId);
                return false;
            }

            try {
                // download data to a temporary file first to prevent partial files from appearing in public directories
                ByteSink output = Files.asByteSink(temporaryDownloadLocation);
                try(InputStream is = connection.getInputStream()) {
                    try(InputStream trackingStream = status.newTrackingInputStream(mId, is)) {
                        output.writeFrom(trackingStream);
                    }
                }
            } catch (Throwable t) {
                connection.disconnect();
            }

            // make sure directory exists, don't worry too much
            File parent = destFile.getParentFile();
            if (parent != null) {
                FileUtils.mkdirsUnchecked(destFile.getParentFile());
            }

            if (destFile.getPath().endsWith(".mp3")) {
                try {
                    writeMetadata(temporaryDownloadLocation, destFile.getParentFile(), trackInfo.get(10, TimeUnit.SECONDS));
                } catch (TimeoutException e) {
                    OSLog.w("Timeout waiting for track info metadata during download", e);
                } catch (InterruptedException e) {
                    // ignore
                } catch (ExecutionException e) {
                    OSLog.e("Problem getting track info metadata during download", e);
                }
            }

            Files.copy(temporaryDownloadLocation, destFile);
            FileUtils.deleteChecked(temporaryDownloadLocation);

            status.markSuccess(destFile.length());
            status.update(mId);

            return true;
        } catch (IOException e) {
            OSLog.e("Error writing downloaded file", e);
            String message = e.getMessage();
            if (message == null) {
                if (e instanceof SocketTimeoutException) {
                    message = "Server timed out";
                } else {
                    message = "Unspecified error: " + e.toString();
                }
            }
            status.markFailed(message, true);
            status.update(mId);
            return false;
        }
    }

    @Nonnull
    private File getTemporaryDownloadLocation() {
        return new File(getDownloadTempDirectory(getContext()), "downloadInProgress." + mId + ".media");
    }

    private static class ScanClientProxy implements MediaScannerConnectionClient {
        @Nonnull
        final private File mFile;

        @GuardedBy("this")
        @Nullable
        private MediaScannerConnection mConnection;

        ScanClientProxy(File scanFile) {
            mFile = scanFile;
        }

        synchronized void initConnection(MediaScannerConnection connection) {
            mConnection = connection;
        }

        @Nullable
        synchronized MediaScannerConnection getConnection() {
            return mConnection;
        }

        void releaseConnection() {

            MediaScannerConnection conn;
            synchronized (this) {
                conn = mConnection;
                mConnection = null;
            }

            if (conn != null) {
                conn.disconnect();
            }
        }

        @Override
        public void onMediaScannerConnected() {
            MediaScannerConnection connection = getConnection();
            if (connection != null) {
                connection.scanFile(mFile.getAbsolutePath(), null);
            }
        }

        @Override
        public void onScanCompleted(String path, Uri uri) {
            releaseConnection();
        }
    }
}
