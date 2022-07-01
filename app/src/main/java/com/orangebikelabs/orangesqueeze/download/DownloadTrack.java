/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.net.Uri;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.FileUtils;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.TrackInfo;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class DownloadTrack implements Comparable<DownloadTrack> {
    static private final AtomicLong sAdapterIdGenerator = new AtomicLong(0L);

    final private String mId;

    @GuardedBy("this")
    private boolean mReady = false;

    @GuardedBy("this")
    @Nonnull
    private TrackInfo mTrackInfo = TrackInfo.absent();

    @GuardedBy("this")
    @Nullable
    private String mErrorString;

    @GuardedBy("this")
    private boolean mSelected;

    @GuardedBy("this")
    private boolean mTranscoded;

    @GuardedBy("this")
    @Nonnull
    private String mTranscodeExtension = "";

    @GuardedBy("this")
    @Nonnull
    private String mTranscodeParams = "";

    @GuardedBy("this")
    private boolean mCachedDestinationExists;

    @GuardedBy("this")
    @Nullable
    private File mDownloadLocation;

    @GuardedBy("this")
    @Nullable
    private Long mExpectedSize;

    final private long mAdapterId = sAdapterIdGenerator.incrementAndGet();

    final private AtomicReference<String> mCachedStringRepresentation = new AtomicReference<>();

    public DownloadTrack(String id, String initialTrackName) {
        mId = id;
        mSelected = true;
        mCachedStringRepresentation.set(initialTrackName);

        refreshPreferences();
    }

    /**
     * called to update various fields drawn from preferences
     */
    final synchronized public void refreshPreferences() {
        SBPreferences prefs = SBPreferences.get();

        mTranscoded = false;
        mTranscodeExtension = "";
        mTranscodeParams = "";

        if (prefs.isTranscodingEnabled() && SBContextProvider.get().getServerStatus().getVersion().compareTo("7.6") >= 0) {
            mTranscodeExtension = prefs.getTranscodeFormat();
            mTranscodeParams = "?" + Joiner.on("&").join(prefs.getTranscodeOptions());

            String format = mTrackInfo.getContentType();
            if (!Objects.equal(mTranscodeExtension, format)) {
                mTranscoded = true;
            }
        }

        mDownloadLocation = prefs.getDownloadLocation();

        File destinationFile = getDestinationFile();
        mCachedDestinationExists = destinationFile.exists();
        mSelected = !mCachedDestinationExists;

        String trackNum = mTrackInfo.getTrackNumber().orNull();
        StringBuilder buffer = new StringBuilder();
        if (trackNum != null) {
            buffer.append(trackNum);
            buffer.append(". ");
        }
        buffer.append(getTrack());

        String artistName = getArtist();
        if (artistName.length() != 0) {
            buffer.append(" (");
            buffer.append(artistName);
            buffer.append(")");
        }

        String albumName = mTrackInfo.getTrackAlbum();
        if (albumName.length() != 0) {
            buffer.append(" - ");
            buffer.append(albumName);
        }
        if (mCachedDestinationExists) {
            buffer.append(" [EXISTS]");
        }
        mCachedStringRepresentation.set(buffer.toString());
    }

    public long getAdapterId() {
        return mAdapterId;
    }

    @Nonnull
    public String getId() {
        return mId;
    }

    @Nullable
    synchronized public Long getExpectedLength() {
        return mExpectedSize;
    }

    synchronized public boolean isDownloadable() {
        return mReady && mErrorString == null;
    }

    synchronized public boolean isTranscoded() {
        return mTranscoded;
    }

    synchronized public boolean isReady() {
        return mReady;
    }

    synchronized public void markError(String error) {
        mReady = true;
        mErrorString = error;
    }

    synchronized public void setTrackInfo(TrackInfo trackInfo) {
        mTrackInfo = trackInfo;
        mReady = true;

        refreshPreferences();
    }

    @Nonnull
    synchronized public String getTrack() {
        return mTrackInfo.getTrackName();
    }

    @Nonnull
    synchronized public String getArtist() {
        String value = mTrackInfo.getAlbumArtist();
        if (value.length() == 0) {
            value = mTrackInfo.getTrackArtist();
        }
        return value;
    }

    @Nonnull
    synchronized public String getAlbum() {
        return mTrackInfo.getTrackAlbum();
    }

    synchronized public boolean isSelected() {
        return mSelected;
    }

    synchronized public void setSelected(boolean selected) {
        mSelected = selected;
    }

    @Nonnull
    synchronized public Uri getSourceUri() {
        ConnectionInfo ci = SBContextProvider.get().getConnectionInfo();

        String suffix = "";
        if (mTranscoded) {
            suffix = "." + mTranscodeExtension + mTranscodeParams;
        }
        return Uri.parse("http://" + ci.getServerHost() + ":" + ci.getServerPort() + "/music/" + mId + "/download" + suffix);
    }

    @Nonnull
    synchronized private String getBaseFilenameComponent() {
        String retval;
        String url = mTrackInfo.getTrackUrl();
        int ndx = url.lastIndexOf("/");
        if (ndx != -1) {
            retval = url.substring(ndx + 1);
        } else {
            String format = mTrackInfo.getContentType();
            if (Strings.isNullOrEmpty(format)) {
                format = "mp3";
            } else if (format.equals("flc")) {
                format = "flac";
            }

            String trackNumber = mTrackInfo.getTrackNumber().orNull();
            if (trackNumber != null) {
                retval = trackNumber + ". " + mTrackInfo.getTrackName() + "." + format;
            } else {
                retval = "file " + mId + "." + format;
            }
        }
        if (mTranscoded) {
            mExpectedSize = null;
            // get alternate base filename
            ndx = retval.lastIndexOf(".");
            if (ndx != -1) {
                retval = retval.substring(0, ndx) + "." + mTranscodeExtension;
            }
        } else {
            mExpectedSize = mTrackInfo.getFilesize();
        }
        return retval;
    }

    @Nonnull
    public synchronized String getDestinationSubpath() {
        String artistName = getArtist();
        String albumName = mTrackInfo.getTrackAlbum();

        if (artistName.length() == 0) {
            artistName = "Unknown Artist";
        }
        if (albumName.length() == 0) {
            albumName = "Unknown Album";
        }

        String decoded = Uri.decode(getBaseFilenameComponent());
        OSAssert.assertNotNull(decoded, "decoded should never be null");

        return FileUtils.sanitizeFilename(artistName) + "/" + FileUtils.sanitizeFilename(albumName) + "/"
                + FileUtils.sanitizeFilename(decoded);
    }

    public synchronized boolean destinationExists() {
        return mCachedDestinationExists;
    }

    @Nonnull
    public synchronized File getDestinationFile() {
        return new File(mDownloadLocation, getDestinationSubpath());
    }

    @Override
    @Nonnull
    public String toString() {
        return mCachedStringRepresentation.get();
    }

    @Override
    public synchronized int hashCode() {
        return Objects.hashCode(mId);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DownloadTrack)) {
            return false;
        }

        DownloadTrack another = (DownloadTrack) o;
        return Objects.equal(another.mId, mId);
    }

    @Override
    public int compareTo(DownloadTrack another) {
        String destSubpath = getDestinationSubpath();
        String otherDestSubpath = another.getDestinationSubpath();

        boolean destExists = destinationExists();
        boolean otherDestExists = another.destinationExists();

        // @formatter:off
        return ComparisonChain.start()
                .compareFalseFirst(destExists, otherDestExists)
                .compare(destSubpath, otherDestSubpath, Ordering.natural().nullsLast())
                .compare(mId, another.mId)
                .result();

        // @formatter:on
    }

}