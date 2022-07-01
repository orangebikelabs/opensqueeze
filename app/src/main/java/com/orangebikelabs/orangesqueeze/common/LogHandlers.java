/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.ContentObservable;
import android.util.Log;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/**
 * Log handler methods, always executed on the log handler thread.
 */
class LogHandlers {
    private static final String TEMP_CONSOLIDATED_LOGS = "consolidated.log.tmp";
    private static final String CONSOLIDATED_LOGS = "consolidated.log";

    private static final String LOGFILE_BASE = "diagnostics_partial.log";
    private static final long ROLLOVER_THRESHOLD = Constants.MB;

    final static private ContentObservable sLogObservable = new ContentObservable();

    final static private ObjectMapper sJsonObjectMapper;
    final static private Scheduler sLogScheduler;

    static {
        sJsonObjectMapper = new ObjectMapper();
        sJsonObjectMapper.configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, true);
        sJsonObjectMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        sLogScheduler = Schedulers.from(OSExecutors.newSingleThreadScheduledExecutor("logging"));
    }

    // all static, non-final fields are accessed only from the single log executor thread so they need not be synchronized
    static private File sPrimaryLogFile;

    @Nullable
    static private CountingSink sCountingSink;

    @Nullable
    static private OutputStreamWriter sPrimaryWriter;

    /**
     * the rollover log file
     */
    static private File sSecondaryLogFile;

    /**
     * the size that the primary log file was when we initialized the logging subsystem
     */
    static private long sSizeAdjustment;

    /**
     * increments to associate log entries with pretty-printed json
     */
    static private long sJsonIdentifier;

    /**
     * the temporary/working consolidated log file
     */
    static private File sTempConsolidatedLogFile;
    /**
     * the consolidated log file
     */
    static private File sConsolidatedLogFile;

    /**
     * the date format, accessed ONLY from logging thread
     */
    @Nullable
    static private SimpleDateFormat sDateFormat;

    @SuppressWarnings("StringBufferField")
    static private StringBuffer sDateFormatBuffer = new StringBuffer();

    static void init(final Context context) {
        sLogScheduler.scheduleDirect(() -> {
            File dir = context.getFilesDir();
            if (dir == null) {
                return;
            }

            File logDir = new File(dir, "logs");
            if (!logDir.exists()) {
                FileUtils.mkdirsChecked(logDir);
            }

            sPrimaryLogFile = new File(logDir, LOGFILE_BASE);
            sSecondaryLogFile = new File(logDir, LOGFILE_BASE + ".0");

            sTempConsolidatedLogFile = new File(logDir, TEMP_CONSOLIDATED_LOGS);
            sConsolidatedLogFile = new File(logDir, CONSOLIDATED_LOGS);
        });
    }

    static void clear() {
        sLogScheduler.scheduleDirect(new ClearLogs());
    }

    static void add(int level, String tag, @Nullable String msg, @Nullable Throwable t) {
        if(msg == null) {
            msg = "";
        }
        final String loggableMessage = buildStackTraceMessage(msg, t);

        Log.println(level, tag, loggableMessage);

        sLogScheduler.scheduleDirect(new LogMessageCallable(level, tag, loggableMessage));
    }

    static void add(int level, String tag, @Nullable String msg, @Nullable JsonNode node, @Nullable Throwable t) {
        final long id = sJsonIdentifier++;
        if(msg == null) {
            msg = "";
        }
        final String loggableMessage = buildStackTraceMessage("[JSON_LOG_ID: " + id + "] " + msg, t);

        Log.println(level, tag, loggableMessage);

        sLogScheduler.scheduleDirect(new LogJsonCallable(level, tag, loggableMessage, node, id));
    }

    @Nonnull
    static ContentObservable getLogObservable() {
        return sLogObservable;
    }

    @Nonnull
    static Single<File> getConsolidatedLog() {
        return Single
                .fromCallable(new AssembleLogs())
                .subscribeOn(sLogScheduler);
    }

    static private class ClearLogs implements Runnable {
        @Override
        public void run() {
            removeLogFiles();
        }
    }

    static private class AssembleLogs implements Callable<File> {
        @Override
        public File call() throws Exception {
            if (sConsolidatedLogFile.exists()) {
                FileUtils.deleteChecked(sConsolidatedLogFile);
            }
            if (sTempConsolidatedLogFile.exists()) {
                FileUtils.deleteChecked(sTempConsolidatedLogFile);
            }

            final long maxLength = 500 * Constants.KB;
            long writeLength = 0;

            BufferedSink consolidatedLogs = Okio.buffer(Okio.appendingSink(sTempConsolidatedLogFile));
            try {
                if (sSecondaryLogFile.isFile()) {
                    BufferedSource src = Okio.buffer(Okio.source(sSecondaryLogFile));
                    try {
                        writeLength += consolidatedLogs.writeAll(src);
                    } finally {
                        src.close();
                    }
                }
                if (sPrimaryLogFile.isFile()) {
                    BufferedSource src = Okio.buffer(Okio.source(sPrimaryLogFile));
                    try {
                        writeLength += consolidatedLogs.writeAll(src);
                    } finally {
                        src.close();
                    }
                }
            } finally {
                consolidatedLogs.close();
            }

            if (writeLength > maxLength) {
                BufferedSink finalLog = Okio.buffer(Okio.appendingSink(sConsolidatedLogFile));
                try {
                    BufferedSource src = Okio.buffer(Okio.source(sTempConsolidatedLogFile));
                    try {
                        src.skip(writeLength - maxLength);
                        finalLog.writeAll(src);
                    } finally {
                        src.close();
                    }
                } finally {
                    finalLog.close();
                }

                //noinspection ResultOfMethodCallIgnored
                sTempConsolidatedLogFile.delete();
            } else {
                //noinspection ResultOfMethodCallIgnored
                sTempConsolidatedLogFile.renameTo(sConsolidatedLogFile);
            }
            return sConsolidatedLogFile;
        }
    }

    static private class LogMessageCallable implements Runnable {
        @Nonnull
        final protected String mMessage;

        final protected int mLevel;

        @Nonnull
        final protected String mTag;

        LogMessageCallable(int level, String tag, String message) {
            mLevel = level;
            mTag = tag;
            mMessage = message;
        }

        /**
         * returns true if log files are open and ready
         */
        @Override
        public void run() {
            if (prepareLogFiles()) {
                try {
                    OSAssert.assertNotNull(sPrimaryWriter, "shouldn't be null after call to prepareLogFiles");

                    writeLogHeader(mLevel, mTag);
                    sPrimaryWriter.write(mMessage);
                    sPrimaryWriter.write('\n');
                    sPrimaryWriter.flush();

                    sLogObservable.dispatchChange(false, null);
                } catch (IOException e) {
                    OSLog.w(e.getMessage(), e);
                }
            }
        }
    }

    static private class LogJsonCallable extends LogMessageCallable {
        @Nullable
        final protected JsonNode mNode;

        final protected long mId;

        LogJsonCallable(int level, String tag, String message, @Nullable JsonNode node, long id) {
            super(level, tag, message);
            mNode = node;
            mId = id;
        }

        @Override
        public void run() {
            super.run();

            try {
                OSAssert.assertNotNull(sCountingSink, "shouldn't be null after call to superclass");
                OSAssert.assertNotNull(sPrimaryWriter, "shouldn't be null after call to superclass");

                long mark = sCountingSink.getWriteCount() + sSizeAdjustment;
                if (mNode != null) {
                    sJsonObjectMapper.writerWithDefaultPrettyPrinter()
                            .withFeatures(JsonWriteFeature.ESCAPE_NON_ASCII)
                            .writeValue(sPrimaryWriter, mNode);
                    sPrimaryWriter.write('\n');
                    sPrimaryWriter.flush();
                }

                byte[] buffer = new byte[2048];
                RandomAccessFile file = new RandomAccessFile(sPrimaryLogFile, "r");
                try {
                    file.seek(mark);

                    int len;
                    while ((len = file.read(buffer)) != -1) {
                        // intentionally using single-byte charset because multi-byte characters may get truncated at buffer boundaries
                        String val = new String(buffer, 0, len, "US-ASCII");
                        Log.println(mLevel, mTag, "[JSON_LOG_ID: " + mId + "]: " + val);

                    }
                } finally {
                    file.close();
                }
            } catch (IOException e) {
                OSLog.w(e.getMessage(), e);
            }
        }
    }

    /**
     * must be executed from the log thread
     */
    @SuppressLint("SimpleDateFormat")
    protected static String formatCurrentDate() {
        if (sDateFormat == null) {
            try {
                sDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
            } catch (IllegalArgumentException e) {
                sDateFormat = new SimpleDateFormat();
            }
        }
        sDateFormatBuffer.setLength(0);
        return sDateFormat.format(new Date(), sDateFormatBuffer, new FieldPosition(0)).toString();
    }

    /**
     * must be executed from the log thread
     */
    protected static void writeLogHeader(int level, String tag) throws IOException {
        OSAssert.assertNotNull(sPrimaryWriter, "shouldn't be null here");

        sPrimaryWriter.write(formatCurrentDate());
        sPrimaryWriter.write(' ');
        sPrimaryWriter.write(Strings.padStart(tag, 12, ' '));
        sPrimaryWriter.write(' ');

        char c = '?';

        switch (level) {
            case Log.ASSERT:
                c = 'A';
                break;
            case Log.DEBUG:
                c = 'D';
                break;
            case Log.ERROR:
                c = 'E';
                break;
            case Log.INFO:
                c = 'I';
                break;
            case Log.VERBOSE:
                c = 'V';
                break;
            case Log.WARN:
                c = 'W';
                break;
            default:
                OSAssert.assertNotReach();
                break;
        }
        sPrimaryWriter.write(' ');
        sPrimaryWriter.write(c);
        sPrimaryWriter.write(' ');
        sPrimaryWriter.write(' ');
    }

    /**
     * must be executed from the log thread
     */
    protected static void removeLogFiles() {
        try {
            closePrimaryWriter();

            if (sPrimaryLogFile.exists()) {
                FileUtils.deleteChecked(sPrimaryLogFile);
            }

            if (sSecondaryLogFile.exists()) {
                FileUtils.deleteChecked(sSecondaryLogFile);
            }

            if (sConsolidatedLogFile.exists()) {
                FileUtils.deleteChecked(sConsolidatedLogFile);
            }

            if (sTempConsolidatedLogFile.exists()) {
                FileUtils.deleteChecked(sTempConsolidatedLogFile);
            }
        } catch (IOException e) {
            Log.w(OSLog.Tag.DEFAULT.getTag(), e.getMessage(), e);
        }
    }

    protected static void closePrimaryWriter() throws IOException {
        if (sPrimaryWriter != null) {
            sCountingSink = null;
            try {
                sPrimaryWriter.close();
            } finally {
                sPrimaryWriter = null;
            }
        }
    }

    /**
     * must be executed from the log thread
     */
    protected static boolean prepareLogFiles() {
        if (sCountingSink != null && (sCountingSink.getWriteCount() + sSizeAdjustment) > ROLLOVER_THRESHOLD) {
            try {
                closePrimaryWriter();

                // perform rollover
                if (sSecondaryLogFile.exists()) {
                    FileUtils.deleteChecked(sSecondaryLogFile);
                }
                FileUtils.move(sPrimaryLogFile, sSecondaryLogFile);
            } catch (IOException e) {
                Log.w(OSLog.Tag.DEFAULT.getTag(), e.getMessage(), e);
            }
        }

        if (sPrimaryWriter == null) {
            sSizeAdjustment = 0L;
            if (sPrimaryLogFile.isFile()) {
                sSizeAdjustment = sPrimaryLogFile.length();
            }
            try {
                sCountingSink = MoreOkio.counting(Okio.appendingSink(sPrimaryLogFile));
                sPrimaryWriter = new OutputStreamWriter(Okio.buffer(sCountingSink).outputStream(), "UTF-8");
            } catch (IOException e) {
                Log.w(OSLog.Tag.DEFAULT.getTag(), e.getMessage(), e);
            }
        }

        return sPrimaryWriter != null;
    }

    /**
     * can be executed from any thread
     */
    @Nonnull
    protected static String buildStackTraceMessage(String msg, @Nullable Throwable t) {
        String logMessage;
        if (t != null) {
            logMessage = msg + '\n' + Log.getStackTraceString(t);
        } else {
            logMessage = msg;
        }
        return logMessage;
    }
}