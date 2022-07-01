/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.net;

import android.content.Context;
import android.util.Base64;

import com.google.common.reflect.Reflection;
import com.google.common.util.concurrent.MoreExecutors;
import com.orangebikelabs.orangesqueeze.app.JsonRpcRequest;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.FutureResult;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class BasicHttpServerCredentials extends SBCredentials {
    final private Context mContext;
    final private String mHostname;
    final private int mPort;
    final private SBContext mFakeContext;

    private String mUsername;
    private char[] mPassword;

    public BasicHttpServerCredentials(final Context context, String hostname, int port) {
        mContext = context;
        mHostname = hostname;
        mPort = port;

        final ConnectionInfo connecting = ConnectionInfo.newInstance(false, 0, hostname, port, hostname, null, null, null);
        final SBContext actualContext = SBContextProvider.get();

        // create a fake SBContext with connection info tied to the hostname and
        // port we've specified so that we can use our ServerStatusTask class to
        // test the connection
        mFakeContext = Reflection.newProxy(SBContext.class, (proxy, method, args) -> {
            String methodName = method.getName();
            switch (methodName) {
                case "getConnectionInfo":
                    return connecting;
                case "getPlayerId":
                    return null;
                case "isConnecting":
                    return Boolean.FALSE;
                case "getConnectionCredentials":
                    return BasicHttpServerCredentials.this;
                case "awaitConnection":
                    return Boolean.TRUE;
                default:
                    return method.invoke(actualContext, args);
            }
        });
    }

    public void set(String username, char[] password) {
        mUsername = username;
        mPassword = password.clone();
    }

    @Override
    @Nullable
    public HttpUtils.Header getHeader(URL url) {
        HttpUtils.Header header = null;
        if (matches(url, mHostname, mPort)) {
            String encoded = getEncodedCredentials();
            if (encoded != null) {
                header = new HttpUtils.Header("Authorization", "Basic " + encoded);
            }
        }
        return header;
    }

    @Override
    public boolean checkCredentials(long duration, TimeUnit units) throws InterruptedException {
        boolean success = false;
        try {
            // set the fake context
            JsonRpcRequest request = new JsonRpcRequest(mContext, mFakeContext, Arrays.asList("serverstatus", "0", "1"));
            request.setTimeout(duration, units);

            FutureResult futureResult = request.submit(MoreExecutors.newDirectExecutorService());
            futureResult.get(duration, units);
            success = true;
        } catch (TimeoutException | ExecutionException e) {
            OSLog.i(e.getMessage(), e);
        }
        return success;
    }

    @Nullable
    private String getEncodedCredentials() {
        String retval = null;
        if (mUsername != null && mPassword != null) {
            String usernameAndPassword = mUsername + ":" + new String(mPassword);
            try {
                byte[] bytes = usernameAndPassword.getBytes("ISO8859_1");
                retval = Base64.encodeToString(bytes, Base64.NO_WRAP);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return retval;
    }
}
