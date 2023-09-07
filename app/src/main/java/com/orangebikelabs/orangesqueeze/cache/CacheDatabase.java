/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.cache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteException;

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.orangebikelabs.orangesqueeze.database.OSDatabase;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.CacheContent;
import com.orangebikelabs.orangesqueeze.common.FileUtils;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.ServerContent;
import com.orangebikelabs.orangesqueeze.common.event.TriggerMenuLoad;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccessKt;
import com.squareup.sqldelight.db.SqlCursor;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQueryBuilder;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.util.Optional;

import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_EXPIRES_TIMESTAMP;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_ID;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_ITEMSTATUS;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_KEY;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_KEYHASH;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_LASTUSED_TIMESTAMP;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_SERVERSCAN_TIMESTAMP;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_VALUE;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.COLUMN_CACHE_VALUE_SIZE;
import static com.orangebikelabs.orangesqueeze.common.CacheContent.TABLE_CACHE;

/**
 * Accessor for cache database operations, including expansion files.
 */
public class CacheDatabase {

    /**
     * this is the threshold which defines how far under the upper-limit size we shrink to when a shrink operation runs
     */
    private static final float CACHE_SHRINK_THRESHOLD_FACTOR = 0.85f;

    // after 4K, use a separate file
    final private static long CACHE_EXPANSION_THRESHOLD = 4096;

    /**
     * reference to the database object
     */
    final protected SupportSQLiteDatabase mDatabase;

    final protected OSDatabase mNewDatabase;

    final protected CacheConfiguration mConfiguration;

    CacheDatabase(Context context, CacheConfiguration configuration) {
        mNewDatabase = DatabaseAccess.getInstance(context);
        mDatabase = DatabaseAccessKt.getLegacyDatabase(mNewDatabase);
        mConfiguration = configuration;
    }

    public void wipe() {
        // wipe any cached menus
        mNewDatabase.getGlobalQueries().wipeStoredMenus();

        BusProvider.getInstance().post(new TriggerMenuLoad());

        // purge with no filter
        OSLog.i(OSLog.Tag.CACHE, "Purging all entries in SQLite cache");
        int cnt = purgeEntries("", Collections.emptyList());

        if (!mDatabase.inTransaction()) {
            OSLog.i(OSLog.Tag.CACHE, "Vacuuming SQLite database");
            try {
                mDatabase.execSQL("VACUUM");
            } catch (SQLiteException e) {
                // ignore failures
                OSLog.i(e.getMessage(), e);
            }
        }

        OSLog.i(OSLog.Tag.CACHE, cnt + " entries purged from SQLite cache due to cache wipe request");

        // current cache format has no directories at all, remove any we come across
        File[] files = mConfiguration.getExpandedCacheDir().listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    OSLog.i(OSLog.Tag.CACHE, "Cache cleanup: Discovered directory from old cache format, deleting. " + f);
                    FileUtils.deleteDirectory(f);
                }
            }
        }

    }

    /**
     * remove the specified cache entry from the database
     */
    public boolean removeFromDatabase(CacheEntry entry) {
        String selection = getCacheSelectionClause();
        String[] args = getCacheSelectionArguments(entry);

        int count = purgeEntries(selection, Arrays.asList(args));
        return count != 0;
    }

    /**
     * build a cache sql selection clause
     */
    @Nonnull
    public String getCacheSelectionClause(String... extras) {
        // WARNING: if this changes, also need to change cleanup() method implementation to match
        StringBuilder selection = new StringBuilder();
        OSAssert.assertEquals(selection.length(), 0, "length should be zero leaving the get()");

        selection.append(ServerContent.COLUMN_FK_SERVER_ID);
        selection.append(" = ?");

        selection.append(" AND ");
        selection.append(COLUMN_CACHE_KEYHASH);
        selection.append(" = ?");

        selection.append(" AND ");
        selection.append(COLUMN_CACHE_KEY);
        selection.append(" = ?");

        for (String x : extras) {
            selection.append(" AND ");
            selection.append(x);
        }
        return selection.toString();
    }

    /**
     * get a list of arguments for corresponding sql
     */
    @Nonnull
    public String[] getCacheSelectionArguments(CacheEntry entry, String... extras) {
        final int PREAMBLE_LENGTH = 3;

        String[] retval = new String[PREAMBLE_LENGTH + extras.length];
        int ndx = 0;

        // PREAMBLE_LENGTH number of items here
        retval[ndx++] = Long.toString(entry.getServerId());
        retval[ndx++] = Long.toString(entry.getKeyHash());
        retval[ndx++] = entry.getKey();

        for (String k : extras) {
            retval[ndx++] = k;
        }
        OSAssert.assertTrue(ndx == retval.length, "didn't match up preamble length properly");
        OSAssert.assertNotNull(retval[ndx - 1], "shouldn't be null");
        return retval;
    }

    public int purgeEntries(String where, List<String> args) {
        // first, purge any entries stored completely in the cache
        String nonExternalWhere = where;
        if (nonExternalWhere.length() != 0) {
            nonExternalWhere += " AND ";
        }
        nonExternalWhere += COLUMN_CACHE_VALUE + " IS NOT NULL";

        int updateCount = mDatabase.delete(TABLE_CACHE, nonExternalWhere, args.toArray(new String[0]));

        // now, purge any entries with values stored outside the cache

        String externalWhere = where;
        if (externalWhere.length() != 0) {
            externalWhere += " AND ";
        }
        externalWhere += COLUMN_CACHE_VALUE + " IS NULL";

        EntryDeleter deleter = new EntryDeleter();
        SupportSQLiteQuery query = SupportSQLiteQueryBuilder.builder(TABLE_CACHE)
                .columns(new String[]{COLUMN_CACHE_ID})
                .selection(externalWhere, args.toArray(new String[0]))
                .create();
        try(Cursor cursor = mDatabase.query(query)) {
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(0);
                    deleter.delete(id);
                } while (cursor.moveToNext());
            }
        }
        return deleter.getCount() + updateCount;
    }

    /**
     * get the expansion filename for the supplied cache rowid
     */
    @Nonnull
    public File getExpansionFile(long cacheId) {
        return new File(mConfiguration.getExpandedCacheDir(), cacheId + ".cached");
    }


    /**
     * purges cache entries that are no longer up-to-date because of the server scan timestamp
     */
    public void cleanupPurgeServerscan() {
        Long lastScan = SBContextProvider.get().getServerStatus().getLastScanTime();
        if (lastScan != null) {
            // remove any server status items that have expired due to rescan or whatever
            String where = ServerContent.COLUMN_FK_SERVER_ID + " = ? AND " + COLUMN_CACHE_EXPIRES_TIMESTAMP + " IS NULL AND " + COLUMN_CACHE_SERVERSCAN_TIMESTAMP
                    + " <> ?";
            List<String> args = Arrays.asList(Long.toString(SBContextProvider.get().getServerId()),
                    lastScan.toString());

            int updateCount = purgeEntries(where, args);
            OSLog.d(OSLog.Tag.CACHE, updateCount + " server scan scoped item(s) purged from cache");
        }
    }

    /**
     * purges cache entries that have expired
     */
    public void cleanupPurgeTimeout() {
        // remove any timeout items that have expired
        String where = COLUMN_CACHE_SERVERSCAN_TIMESTAMP + " IS NULL AND " + COLUMN_CACHE_EXPIRES_TIMESTAMP + " < ?";
        List<String> args = Collections.singletonList(Long.toString(System.currentTimeMillis()));

        int updateCount = purgeEntries(where, args);
        OSLog.d(OSLog.Tag.CACHE, updateCount + " timeout scoped item(s) purged from cache");
    }

    @Nonnull
    public Optional<ByteSource> loadEntry(CacheEntry entry, String extraSelection, String extraArg) throws CachedItemNotFoundException {
        String selection = getCacheSelectionClause(extraSelection);
        String[] args = getCacheSelectionArguments(entry, extraArg);
        SupportSQLiteQuery query = SupportSQLiteQueryBuilder.builder(TABLE_CACHE)
                .columns(new String[]{COLUMN_CACHE_ID, COLUMN_CACHE_ITEMSTATUS, COLUMN_CACHE_VALUE})
                .selection(selection, args)
                .create();
        Cursor cursor = mDatabase.query(query);
        if (cursor == null) {
            Reporting.report("expected non-null cursor");
            // problem with query
            return Optional.empty();
        }

        ByteSource retval = null;
        try {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);

                CacheContent.ItemStatus currentStatus = CacheContent.ItemStatus.fromString(cursor.getString(1), CacheContent.ItemStatus.INVALID);
                switch (currentStatus) {
                    case EXTERNAL:
                        File cacheFile = getExpansionFile(id);
                        if (cacheFile.exists()) {
                            retval = Files.asByteSource(cacheFile);
                        } else {
                            throw new CachedItemNotFoundException("expansion file removed");
                        }
                        break;
                    case INTERNAL:
                        byte[] byteData = cursor.getBlob(2);
                        retval = ByteSource.wrap(byteData);
                        break;
                    case INVALID:
                    case NOTFOUND:
                        // always ignore invalid/notfound status in database
                        break;
                }
            }
        } finally {
            cursor.close();
        }

        return Optional.ofNullable(retval);
    }

    public void cleanupShrinkExternalCache() {
        long externalSize = mNewDatabase.getCacheQueries().lookupExternalCacheSize().executeAsOne().longValue();

        // we don't need to shrink the cache
        if (externalSize <= mConfiguration.getMaxExternalSize()) {
            OSLog.d(OSLog.Tag.CACHE, "0 item(s) were shrunk from external storage");
            return;
        }

        final long desiredSize = (long) (CACHE_SHRINK_THRESHOLD_FACTOR * mConfiguration.getMaxExternalSize());

        try {
            try(SqlCursor cursor = mNewDatabase.getCacheQueries().lookupExternalEntriesSortedByDisuse().execute()) {
                EntryDeleter deleter = new EntryDeleter();
                while (externalSize > desiredSize && cursor.next()) {
                    long id = cursor.getLong(0);
                    long rowSize = cursor.getLong(1);
                    deleter.delete(id);

                    // remove the size of the file, as it stands in our records. It's possible it was already gone, but we don't care about that.
                    externalSize -= rowSize;
                }
                OSLog.d(OSLog.Tag.CACHE, deleter.getCount() + " item(s) were shrunk from external storage");
            }
        } catch (IOException e) {
            // this shouldn't really happen
            OSLog.w(e.getMessage(), e);
        }
    }

    public void cleanupShrinkSqliteCache() {
        long internalSize = mNewDatabase.getCacheQueries().lookupInternalCacheSize().executeAsOne().longValue();

        // we don't need to shrink the cache
        if (internalSize <= mConfiguration.getMaxSqliteSize()) {
            OSLog.d(OSLog.Tag.CACHE, "0 item(s) were shrunk from sqlite storage");
            return;
        }

        final long desiredSize = (long) (CACHE_SHRINK_THRESHOLD_FACTOR * mConfiguration.getMaxSqliteSize());
        try {
            try(SqlCursor cursor = mNewDatabase.getCacheQueries().lookupInternalEntriesSortedByDisuse().execute()) {
                EntryDeleter deleter = new EntryDeleter();
                while (internalSize > desiredSize && cursor.next()) {
                    long id = cursor.getLong(0);
                    long rowSize = cursor.getLong(1);
                    deleter.delete(id);

                    // remove the size of the file, as it stands in our records. It's possible it was already gone, but we don't care about that.
                    internalSize -= rowSize;
                }
                OSLog.d(OSLog.Tag.CACHE, deleter.getCount() + " item(s) were shrunk from sqlite storage");
            }
        } catch (IOException e) {
            // shouldn't happen
            OSLog.w(e.getMessage(), e);
        }
    }

    public void markEntry(CacheEntry entry, CacheContent.ItemStatus newStatus) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_CACHE_ITEMSTATUS, newStatus.name());

        String clause = getCacheSelectionClause();
        String[] args = getCacheSelectionArguments(entry);

        int updateCount = mDatabase.update(TABLE_CACHE, SQLiteDatabase.CONFLICT_IGNORE, values, clause, args);
        if (updateCount > 0) {
            try {
                SupportSQLiteQuery query = SupportSQLiteQueryBuilder
                        .builder(TABLE_CACHE)
                        .selection(clause, args)
                        .columns(new String[]{COLUMN_CACHE_ID})
                        .create();
                Cursor cursor = mDatabase.query(query);
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        long rowId = cursor.getLong(0);
                        File expansionFile = getExpansionFile(rowId);
                        if (expansionFile.isFile()) {
                            FileUtils.deleteChecked(expansionFile);
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            } catch (SQLiteDoneException e) {
                // row was deleted
            }
        }
    }

    public void storeEntry(ExecutorService executor, CacheEntry entry, ByteSource byteSource, long estimatedSize, long newExpiresTimestamp) throws IOException {
        Callable<?> callable;
        if (estimatedSize > CACHE_EXPANSION_THRESHOLD) {
            // this uses a file as backing store, just renames it
            callable = new PersistExpandedEntry(entry, byteSource, newExpiresTimestamp);
        } else {
            // saves data in memory before writing to database
            callable = new PersistNormalEntry(entry, byteSource.read(), newExpiresTimestamp);
        }
        executor.submit(callable);
    }

    /**
     * helper class to speed deleting entries properly
     */
    @NotThreadSafe
    public class EntryDeleter {
        private int mDeleteCount;

        public void delete(long id) {
            mNewDatabase.getCacheQueries().deleteWithId(id);
            mDeleteCount++;

            File expansionFile = getExpansionFile(id);
            if (expansionFile.isFile()) {
                FileUtils.deleteChecked(expansionFile);
            }
        }

        public int getCount() {
            return mDeleteCount;
        }
    }

    @Nonnull
    public EntryRenewer newEntryRenewer() {
        return new EntryRenewer();
    }

    public class EntryRenewer implements Closeable {
        private SupportSQLiteStatement mStatement;

        final private Set<CacheEntry> mProcessedItems = new HashSet<>();
        final private long mCurrentTime;

        private int mUpdateCount;
        private int mRenewCount;


        public EntryRenewer() {
            mUpdateCount = 0;
            mCurrentTime = System.currentTimeMillis();
            mDatabase.beginTransaction();
        }

        public int getRenewCount() {
            return mRenewCount;
        }

        public int getUpdateCount() {
            return mUpdateCount;
        }

        public void renewEntry(CacheEntry entry) {
            if (mStatement == null) {
                mStatement = mDatabase.compileStatement(
                        "UPDATE " + TABLE_CACHE + " " +
                                "SET " + COLUMN_CACHE_LASTUSED_TIMESTAMP + " = ? " +
                                "WHERE " + getCacheSelectionClause());
            }
            if (mStatement == null) {
                throw new IllegalStateException("error compiling sql statement");
            }
            mUpdateCount++;

            if (!mProcessedItems.add(entry)) {
                // already processed this item in this renewal transaction, don't do it again
                return;
            }

            mStatement.clearBindings();

            mStatement.bindLong(1, mCurrentTime);
            mStatement.bindLong(2, entry.getServerId());
            mStatement.bindLong(3, entry.getKeyHash());
            mStatement.bindString(4, entry.getKey());
            if (mStatement.executeInsert() != -1) {
                mRenewCount++;
            }
        }

        public void commit() {
            mDatabase.setTransactionSuccessful();
        }

        @Override
        public void close() throws IOException {
            if (mStatement != null) {
                mStatement.close();
                mStatement = null;
            }

            mDatabase.endTransaction();
        }
    }

    private class PersistNormalEntry implements Callable<Void> {
        @Nonnull
        final private CacheEntry mEntry;

        @Nonnull
        final private byte[] mBytes;

        final private long mNewExpiresTimestamp;

        PersistNormalEntry(CacheEntry entry, byte[] byteData, long newExpiresTimestamp) {
            mEntry = entry;
            mNewExpiresTimestamp = newExpiresTimestamp;
            mBytes = byteData;
        }

        @Override
        public Void call() {
            ContentValues values = new ContentValues();

            values.put(COLUMN_CACHE_KEY, mEntry.getKey());
            values.put(COLUMN_CACHE_KEYHASH, mEntry.getKeyHash());
            values.put(ServerContent.COLUMN_FK_SERVER_ID, mEntry.getServerId());

            values.put(COLUMN_CACHE_VALUE, mBytes);
            values.put(CacheContent.COLUMN_CACHE_ITEMSTATUS, CacheContent.ItemStatus.INTERNAL.name());
            values.put(COLUMN_CACHE_VALUE_SIZE, mBytes.length);

            switch (mEntry.getCacheType()) {
                case SERVERSCAN: {
                    Long lastScan = SBContextProvider.get().getServerStatus().getLastScanTime();
                    if (lastScan == null) {
                        // we're scanning,
                        return null;
                    }
                    values.putNull(COLUMN_CACHE_EXPIRES_TIMESTAMP);
                    values.put(COLUMN_CACHE_SERVERSCAN_TIMESTAMP, lastScan);
                    break;
                }
                case TIMEOUT:
                    values.put(COLUMN_CACHE_EXPIRES_TIMESTAMP, mNewExpiresTimestamp);
                    values.putNull(COLUMN_CACHE_SERVERSCAN_TIMESTAMP);
                    break;
                default:
                    throw new IllegalStateException();
            }

            values.put(COLUMN_CACHE_LASTUSED_TIMESTAMP, System.currentTimeMillis());

            // delete any existing rows that match the key and insert a new one
            mDatabase.beginTransaction();
            try {
                String selection = getCacheSelectionClause();
                String[] args = getCacheSelectionArguments(mEntry);

                int purgedCount = purgeEntries(selection, Arrays.asList(args));
                if (purgedCount > 0) {
                    OSLog.i(OSLog.Tag.CACHE, purgedCount + " existing entries purged during cache insert");
                }

                mDatabase.insert(TABLE_CACHE, SQLiteDatabase.CONFLICT_IGNORE, values);
                mDatabase.setTransactionSuccessful();

                if (OSLog.isLoggable(OSLog.DEBUG)) {
                    OSLog.d(OSLog.Tag.CACHE, "Successfully wrote " + mEntry + " to the database cache");
                }

            } finally {
                mDatabase.endTransaction();
            }

            return null;
        }
    }

    private class PersistExpandedEntry implements Callable<Void> {
        @Nonnull
        final private File mTemporaryCacheFile;
        @Nonnull
        final private CacheEntry mEntry;

        final private long mNewExpiresTimestamp;

        final private long mLength;

        PersistExpandedEntry(CacheEntry entry, ByteSource byteSource, long newExpiresTimestamp) throws IOException {
            mEntry = entry;
            mNewExpiresTimestamp = newExpiresTimestamp;
            mTemporaryCacheFile = File.createTempFile("temp", "persist", mConfiguration.getExpandedCacheDir());
            Files.createParentDirs(mTemporaryCacheFile);
            mLength = byteSource.copyTo(Files.asByteSink(mTemporaryCacheFile));
        }

        @Override
        public Void call() {
            ContentValues values = new ContentValues();

            values.put(COLUMN_CACHE_KEY, mEntry.getKey());
            values.put(COLUMN_CACHE_KEYHASH, mEntry.getKeyHash());
            values.put(ServerContent.COLUMN_FK_SERVER_ID, mEntry.getServerId());

            values.put(CacheContent.COLUMN_CACHE_ITEMSTATUS, CacheContent.ItemStatus.EXTERNAL.name());
            values.putNull(COLUMN_CACHE_VALUE);
            values.put(COLUMN_CACHE_VALUE_SIZE, mLength);

            switch (mEntry.getCacheType()) {
                case SERVERSCAN: {
                    Long lastScan = SBContextProvider.get().getServerStatus().getLastScanTime();
                    if (lastScan == null) {
                        // skip write, we're scanning
                        return null;
                    }
                    values.putNull(COLUMN_CACHE_EXPIRES_TIMESTAMP);
                    values.put(COLUMN_CACHE_SERVERSCAN_TIMESTAMP, lastScan);
                    break;
                }
                case TIMEOUT:
                    values.put(COLUMN_CACHE_EXPIRES_TIMESTAMP, mNewExpiresTimestamp);
                    values.putNull(COLUMN_CACHE_SERVERSCAN_TIMESTAMP);
                    break;
                default:
                    throw new IllegalStateException();
            }

            values.put(COLUMN_CACHE_LASTUSED_TIMESTAMP, System.currentTimeMillis());

            // delete any existing rows that match the key and insert a new one
            mDatabase.beginTransaction();
            try {
                String selection = getCacheSelectionClause();
                String[] args = getCacheSelectionArguments(mEntry);

                int purgedCount = purgeEntries(selection, Arrays.asList(args));
                if (purgedCount > 0) {
                    OSLog.i(OSLog.Tag.CACHE, purgedCount + " existing entries purged during cache insert");
                }
                long rowId = mDatabase.insert(TABLE_CACHE, SQLiteDatabase.CONFLICT_IGNORE, values);
                Files.move(mTemporaryCacheFile, getExpansionFile(rowId));
                mDatabase.setTransactionSuccessful();

                if (OSLog.isLoggable(OSLog.DEBUG)) {
                    OSLog.d(OSLog.Tag.CACHE, "Successfully wrote " + mEntry + " to the database cache");
                }
            } catch (IOException e) {
                OSLog.w(OSLog.Tag.CACHE, "Error writing " + mEntry + " to database or filesystem", e);
            } finally {
                mDatabase.endTransaction();
            }

            if (mTemporaryCacheFile.exists()) {
                FileUtils.deleteChecked(mTemporaryCacheFile);
            }

            return null;
        }
    }

}
