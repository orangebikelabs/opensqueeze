/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

/**
 * Constants that are used only for migration logic, etc. These should not be used in any other situation.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class DeprecatedConstants {

    public static final String TABLE_ARTIST = "artist";
    public static final String TABLE_TRACK = "track";
    public static final String TABLE_ALBUM = "album";
    public static final String TABLE_SCAN_PROGRESS = "scanprogress";
    public static final String TABLE_ARTIST_TRACK_XREF = "artisttrackxref";
    public static final String TABLE_ALBUM_TRACK_XREF = "albumtrackxref";

    public static final String COLUMN_LAST_SCAN_TIME = "lastscantime";
    public static final String COLUMN_SCAN_IN_PROGRESS = "scaninprogress";
    public static final String COLUMN_ARTIST_NAME = "artistname";
    public static final String COLUMN_ALBUM_NAME = "albumname";
    public static final String COLUMN_ARTWORK_ID = "artworkid";
    public static final String COLUMN_TRACK_NAME = "trackname";
    public static final String COLUMN_SERVER_MAC_ADDRESS = "servermacaddress";

    public static final String COLUMN_FK_ARTIST_ID = "artistid";
    public static final String COLUMN_FK_ALBUM_ID = "albumid";
    public static final String COLUMN_FK_TRACK_ID = "trackid";
    public static final String COLUMN_FK_ALBUMARTIST_ID = "albumartistid";
}
