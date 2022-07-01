/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.orangebikelabs.orangesqueeze.common.AbsInterruptibleThreadService;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.LoggingInputStream;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.net.JsonRpc.ResultConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.GuardedBy;

/**
 * Thread that handles reading from the streaming squeezebox connection.
 *
 * @author tbsandee@orangebikelabs.com
 */
public class StreamingReader extends AbsInterruptibleThreadService {
    final private StreamingSocket mSocket;

    @GuardedBy("this")
    private JsonRpc.Result mLastResult;

    public StreamingReader(StreamingSocket socket) {
        mSocket = socket;
    }

    @Override
    protected String serviceName() {
        return "StreamingReaderThread";
    }

    @Override
    protected void triggerShutdown() {
        super.triggerShutdown();

        JsonRpc.Result lastResult = getLastResult();
        if (lastResult != null) {
            lastResult.getConnection().disconnect();
        }
    }

    @Override
    protected void run() {
        try {
            internalRun();
        } catch (Exception e) {
            // if we're not still in running state, the exception is because the socket got closed which is normal
            if (isRunning()) {
                // otherwise log it
                OSLog.w(Tag.NETWORK, "Connection Error", e);
            }
        } finally {
            mSocket.onReaderQuit(isRunning());
        }
    }

    private void internalRun() throws IOException, RestartException {
        JsonRpc.Result result = getResult();
        try {
            JsonParser parser = result.getParser();
            JsonToken token;
            // go past the first array start marker
            while ((token = parser.nextToken()) != null && isRunning()) {
                switch (token) {
                    case START_OBJECT:
                        JsonNode node = parser.readValueAsTree();
                        OSLog.d(Tag.NETWORK, "StreamingReader::responseReceived", node);
                        mSocket.onResponseReceived(node);
                        break;
                    case START_ARRAY:
                        break;
                    case END_ARRAY:
                        mSocket.getReadyToWriteLatch().set(Boolean.TRUE);
                        break;
                    default:
                        OSLog.v(Tag.NETWORK, "StreamingReader::ignoredToken = " + token);
                        break;
                }
            }
        } finally {
            result.close();
        }
    }

    protected synchronized JsonRpc.Result getLastResult() {
        return mLastResult;
    }

    protected synchronized void setLastResult(JsonRpc.Result lastResult) {
        mLastResult = lastResult;
    }

    private JsonRpc.Result getResult() throws IOException {
        InputStream is = new HttpResponseInputStream(mSocket.getRawSocket().getInputStream());
        JsonParser parser = JsonHelper.getJsonFactory().createParser(is);
        JsonRpc.Result retval = new JsonRpc.Result(new SocketDisconnect(mSocket.getRawSocket()), parser, is);
        setLastResult(retval);
        return retval;
    }

    /**
     * a custom stream implementation that handles the idiosyncracies of the squeezebox streaming protocol
     */
    private static class HttpResponseInputStream extends InputStream {

        enum State {
            READING_HEADERS, IN_FIXED_CONTENT, READING_CHUNKED_CRLF, READING_CHUNKED_HEADERS, IN_CHUNKED_CONTENT, DISCONNECTED
        }

        private State mState = State.READING_HEADERS;
        private int mContentPos, mContentLength;
        final private PushbackInputStream mInputStream;

        HttpResponseInputStream(InputStream is) {
            if (OSLog.isLoggable(Tag.NETWORKTRACE, OSLog.VERBOSE)) {
                is = new LoggingInputStream(is);
            }
            mInputStream = new PushbackInputStream(is);
        }

        @Override
        public int available() throws IOException {
            return mInputStream.available();
        }

        @Override
        public void close() throws IOException {
            mInputStream.close();
        }

        @Override
        public synchronized void mark(int readlimit) {
            mInputStream.mark(readlimit);
        }

        @Override
        public boolean markSupported() {
            return mInputStream.markSupported();
        }

        @Override
        public synchronized void reset() throws IOException {
            mInputStream.reset();
        }

        @Override
        public long skip(long byteCount) throws IOException {
            return mInputStream.skip(byteCount);
        }

        @Override
        public int read() throws IOException {
            int retval = -1;
            boolean done = false;
            while (!done) {
                if (Thread.interrupted()) {
                    throw new InterruptedIOException();
                }

                switch (mState) {
                    case READING_HEADERS:
                        readHeaders();
                        break;
                    case READING_CHUNKED_HEADERS:
                    case READING_CHUNKED_CRLF:
                        nextChunk();
                        break;
                    case IN_CHUNKED_CONTENT:
                        if (mContentPos < mContentLength) {
                            retval = mInputStream.read();
                            if (retval != -1) {
                                mContentPos++;
                            }
                            done = true;
                        } else {
                            mState = State.READING_CHUNKED_CRLF;
                        }
                        break;
                    case IN_FIXED_CONTENT:
                        if (mContentPos < mContentLength) {
                            retval = mInputStream.read();
                            if (retval != -1) {
                                mContentPos++;
                            }
                            done = true;
                        } else {
                            mState = State.READING_HEADERS;
                        }
                        break;
                    case DISCONNECTED:
                        retval = -1;
                        done = true;
                        break;
                }
            }
            return retval;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            boolean handled = false;
            int retval = -1;
            if (mState == State.IN_FIXED_CONTENT) {
                // if we're reading a chunk of fixed content, go ahead and read
                // as a block
                int remaining = mContentLength - mContentPos;
                if (remaining > 0) {
                    handled = true;
                    int maxRead = Math.min(remaining, count);
                    retval = mInputStream.read(buffer, offset, maxRead);
                    if (retval > 0) {
                        mContentPos += retval;
                    }
                }
            } else if (mState == State.IN_CHUNKED_CONTENT) {
                // if we're reading a chunk of chunked content, go ahead and
                // read as a block
                int remaining = mContentLength - mContentPos;
                if (remaining > 0) {
                    handled = true;
                    int maxRead = Math.min(remaining, count);
                    retval = mInputStream.read(buffer, offset, maxRead);
                    if (retval > 0) {
                        mContentPos += retval;
                    }
                }
            }
            if (!handled) {
                // otherwise go piece-by-piece
                retval = read();
                if (retval != -1) {
                    buffer[offset] = (byte) retval;
                    retval = 1;
                }
            }
            return retval;
        }

        protected void readHeaders() throws IOException {
            Map<String, String> headers = new HashMap<>();

            StringBuilder header = new StringBuilder();
            int consecutiveCrLf = 0;
            int c = mInputStream.read();
            if (c != -1) {
                if (c != 'H') {
                    // sometimes we get a chunk when we're expecting a new HTTP
                    // request, handle it gracefully
                    mState = State.READING_CHUNKED_HEADERS;
                    mInputStream.unread(c);
                    return;
                } else {
                    // we are in new HTTP read mode
                    while (consecutiveCrLf < 4 && (c = mInputStream.read()) != -1) {
                        if (Thread.interrupted()) {
                            throw new InterruptedIOException();
                        }
                        switch (c) {
                            case HttpUtils.LF:
                            case HttpUtils.CR:
                                if (header.length() > 0) {
                                    processHeader(headers, header);
                                }
                                consecutiveCrLf++;
                                break;
                            default:
                                consecutiveCrLf = 0;
                                header.append((char) c);
                                break;
                        }
                    }
                }
            }

            if (c == -1) {
                OSLog.d(Tag.NETWORK, "disconnected");
                mState = State.DISCONNECTED;
            } else if ("chunked".equals(headers.get("Transfer-Encoding"))) {
                mState = State.READING_CHUNKED_HEADERS;
            } else if (headers.containsKey("Content-Length")) {
                mContentLength = Integer.parseInt(headers.get("Content-Length"));
                mContentPos = 0;
                mState = State.IN_FIXED_CONTENT;
            } else {
                OSLog.w(Tag.NETWORK, "Unexpected header set: " + headers);
            }
        }

        /**
         * Read the next chunk.
         *
         * @throws IOException If an IO error occurs.
         */
        protected void nextChunk() throws IOException {
            mContentLength = getChunkSize();
            if (mContentLength < 0) {
                throw new IOException("Negative chunk size");
            }
            mContentPos = 0;
            if (mContentLength == 0) {
                mState = State.READING_HEADERS;
            } else {
                mState = State.IN_CHUNKED_CONTENT;
            }
        }

        protected int getChunkSize() throws IOException {
            // skip CRLF
            if (mState == State.READING_CHUNKED_CRLF) {
                int cr = mInputStream.read();
                int lf = mInputStream.read();
                if ((cr != HttpUtils.CR) || (lf != HttpUtils.LF)) {
                    throw new IOException("CRLF expected at end of chunk");
                }
                mState = State.READING_CHUNKED_HEADERS;
            }

            StringBuilder buffer = new StringBuilder();
            boolean done = false;
            int retval = -1;
            int c;
            while (!done && (c = mInputStream.read()) != -1) {
                if (Thread.interrupted()) {
                    throw new InterruptedIOException();
                }

                switch (c) {
                    case HttpUtils.CR:
                        // ignore
                        break;
                    case HttpUtils.LF:
                        if (buffer.length() > 0) {
                            done = true;
                        }
                        break;
                    case 'H':
                        // this indicates HTTP request header pipelined in
                        mInputStream.unread(c);
                        mState = State.READING_HEADERS;
                        retval = 0;
                        done = true;
                        break;
                    default:
                        buffer.append((char) c);
                        break;
                }
            }

            if (mState == State.READING_CHUNKED_HEADERS) {
                int separator = buffer.indexOf(";");
                if (separator < 0) {
                    separator = buffer.length();
                }

                String chunkSize = buffer.substring(0, separator).trim();
                if (chunkSize.length() == 0) {
                    return 0;
                }
                try {
                    retval = Integer.parseInt(chunkSize, 16);
                } catch (NumberFormatException e) {
                    throw new IOException("Bad chunk header: " + chunkSize);
                }
            }
            return retval;
        }
    }

    protected static void processHeader(Map<String, String> headers, StringBuilder header) {
        if (header.length() > 0) {
            int colon = header.indexOf(":");
            if (colon != -1) {
                headers.put(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
            }
        }
        header.setLength(0);
    }

    private static class SocketDisconnect implements ResultConnection {
        final private Socket mSocket;

        public SocketDisconnect(Socket socket) {
            mSocket = socket;
        }

        @Override
        public void disconnect() {
            try {
                mSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    static class RestartException extends Exception {

        public RestartException(String detailMessage) {
            super(detailMessage);
        }
    }
}
