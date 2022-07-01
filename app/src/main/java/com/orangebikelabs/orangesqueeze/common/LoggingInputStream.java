/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Stream implementation that will log all of the data passed in. This will be inefficient, but it can be useful during debugging.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class LoggingInputStream extends FilterInputStream {
    final private StringBuilder mLogData = new StringBuilder();

    public LoggingInputStream(InputStream _in) {
        super(_in);
    }

    @Override
    public int read() throws IOException {
        int retval = super.read();
        if (retval > 0) {
            mLogData.append((char) retval);
        }

        checkLogData();

        return retval;
    }

    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        int retval = super.read(buffer, offset, count);
        if (retval > 0) {
            for (int i = offset; i < offset + retval; i++) {
                mLogData.append((char) buffer[i]);
            }
        }
        checkLogData();

        return retval;
    }

    public void flushLogData() {
        checkLogData();
    }

    private int mNextCheckIndex = 0;

    private void checkLogData() {
        int breakPoint = -1;
        int ndx;
        for (ndx = mNextCheckIndex; ndx < mLogData.length(); ndx++) {
            if (mLogData.charAt(ndx) == '\n' || mLogData.charAt(ndx) == '\r') {
                breakPoint = ndx;
                break;
            }
        }

        if (breakPoint != -1) {
            String substring = mLogData.substring(0, breakPoint);
            OSLog.v(Tag.NETWORKTRACE, substring);

            mLogData.delete(0, breakPoint);
            while (mLogData.length() > 0 && (mLogData.charAt(0) == '\r' || mLogData.charAt(0) == '\n')) {
                mLogData.deleteCharAt(0);
            }
            mNextCheckIndex = 0;
        } else {
            mNextCheckIndex = ndx;
        }
    }
}
