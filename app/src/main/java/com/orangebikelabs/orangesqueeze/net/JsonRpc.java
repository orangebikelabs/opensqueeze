/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Closer;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.LoggingInputStream;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Methods useful in calling through to JSON RPC services.
 *
 * @author tbsandee@orangebikelabs.com
 */
@NotThreadSafe
public class JsonRpc {

    final static public String JSON_CONTENT_TYPE = "text/json";

    private long mTimeout = 0;

    private boolean mUseGlobalCookies;

    @Nonnull
    private TimeUnit mTimeUnit = Constants.TIME_UNITS;

    @Nullable
    private SBCredentials mConnectionCredentials;

    public JsonRpc() {
    }

    public boolean getUseGlobalCookies() {
        return mUseGlobalCookies;
    }

    public void setUseGlobalCookies(boolean useCookies) {
        mUseGlobalCookies = useCookies;
    }

    public void setTimeout(long timeout, TimeUnit units) {
        mTimeout = timeout;
        mTimeUnit = units;
    }

    public void setCredentials(@Nullable SBCredentials creds) {
        mConnectionCredentials = creds;
    }

    /**
     * execute JSON RPC with firm timeout requirements. Uses a second thread to monitor the connection and abort exactly when requested.
     */
    @Nonnull
    public Result execute(final URL url, @Nullable final JsonNode request) throws JsonRpcException, InterruptedException, IOException {
        if (mTimeout == 0L) {
            Result result = executeWithSimpleTimeout(url, request);
            return result;
        }
        SimpleTimeLimiter limiter = SimpleTimeLimiter.create(OSExecutors.getUnboundedPool());
        try {
            Callable<Result> callable = () -> {
                Result result = executeWithSimpleTimeout(url, request);
                return result;
            };

            return limiter.callWithTimeout(callable, mTimeout, mTimeUnit);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            Throwables.propagateIfPossible(cause, JsonRpcException.class);
            Throwables.propagateIfPossible(cause, IOException.class);
            Throwables.propagateIfPossible(cause, InterruptedException.class);
            if (cause instanceof TimeoutException) {
                throw new SocketTimeoutException();
            }

            throw new IllegalStateException("unexpected exception", cause);
        } catch (TimeoutException | UncheckedTimeoutException e) {
            throw new SocketTimeoutException();
        } catch (Exception e) {
            Throwables.propagateIfPossible(e, JsonRpcException.class);
            Throwables.propagateIfPossible(e, IOException.class);
            Throwables.propagateIfPossible(e, InterruptedException.class);

            throw new IllegalStateException("unexpected exception", e);
        }
    }

    /**
     * execute a JSON RPC request with specific timeout requirements, but don't be overly particular
     */
    @Nonnull
    public Result executeWithSimpleTimeout( URL url, @Nullable final JsonNode request) throws JsonRpcException, InterruptedException {
        if (OSLog.isLoggable(Tag.NETWORK, OSLog.DEBUG)) {
            OSLog.d(Tag.NETWORK, "JsonRpc -> " + url, request);
        }

        Request.Builder builder = HttpUtils.newRequestBuilder(url, mUseGlobalCookies);
        try {
            boolean holdMethod = false;

            Closer closer = Closer.create();
            try {
                Result result = internalExecute(builder, url, closer, request);

                // don't release the connection, it's going to continue to be read elsewhere
                holdMethod = true;

                return result;
            } catch (Throwable t) {
                throw closer.rethrow(t, JsonRpcException.class);
            } finally {
                if (!holdMethod) {
                    closer.close();
                }
            }
        } catch (InterruptedIOException e) {
            throw new InterruptedException();
        } catch (IOException e) {
            throw new JsonRpcException(e);
        }
    }

    @Nonnull
    private Result internalExecute(Request.Builder builder, URL url, Closer closer, @Nullable JsonNode request) throws JsonRpcException, IOException {
        builder.header("Accept-Charset", Charsets.UTF_8.name());
        builder.header("Content-Type", JSON_CONTENT_TYPE);
        builder.header("Accept-Encoding", "");

        if (mConnectionCredentials != null) {
            mConnectionCredentials.apply(builder, url);
        }

        if (request != null) {
            String data = JsonHelper.toString(request);
            MediaType mt = MediaType.parse(JSON_CONTENT_TYPE);
            builder.post(RequestBody.create(mt, data));
        }

        OkHttpClient client = HttpUtils.newHttpClient();
        if (mTimeout != 0) {
            client = client.newBuilder().readTimeout(mTimeout, mTimeUnit).build();
        } else {
            client = client.newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
        }
        Call httpCall = client.newCall(builder.build());
        Response response = httpCall.execute();

        int responseCode = response.code();
        ResponseBody body = response.body();
        if(body == null) {
            throw new JsonRpcException(new Exception("null body"));
        }
        if (responseCode != HttpURLConnection.HTTP_OK) {
            body.close();
            throw new JsonRpcException(responseCode);
        }

        // no need to use buffered I/O, Jackson does this already
        InputStream is = closer.register(body.byteStream());
        OSAssert.assertNotNull(is, "stream should never be null");

        if (OSLog.isLoggable(Tag.NETWORKTRACE, OSLog.VERBOSE)) {
            is = new LoggingInputStream(is);
        }
        JsonParser parser = JsonHelper.getJsonFactory().createParser(is);
        return new Result(new HttpAbort(httpCall), parser, is);
    }

    public static class Result implements Closeable {
        @Nonnull
        final private JsonParser mParser;

        @Nonnull
        final private ResultConnection mConnection;

        @Nonnull
        final private InputStream mInputStream;

        @GuardedBy("this")
        private JsonNode mJsonNode;

        Result(ResultConnection connection,  JsonParser parser,  InputStream inputStream) {
            mParser = parser;
            mConnection = connection;
            mInputStream = inputStream;
        }

        @Nonnull
        public JsonParser getParser() {
            return mParser;
        }

        @Nonnull
        public ResultConnection getConnection() {
            return mConnection;
        }

        @Nonnull
        synchronized public JsonNode readValueAsTree() throws IOException {
            if (mJsonNode == null) {
                mJsonNode = mParser.readValueAsTree();
                if (mJsonNode == null) {
                    throw new IOException("error reading JSON node");
                }
            }
            return mJsonNode;
        }

        @Nonnull
        public Iterable<ObjectNode> getObjects() throws IOException {
            JsonNode node = readValueAsTree();
            return JsonHelper.getObjects(node);
        }

        @Override
        public void close() throws IOException {
            mInputStream.close();
        }
    }

    public interface ResultConnection {
        void disconnect();
    }

    private static class HttpAbort implements ResultConnection {
        @Nonnull
        final private Call mCall;

        public HttpAbort(Call call) {
            mCall = call;
        }

        @Override
        public void disconnect() {
            mCall.cancel();
        }
    }
}
