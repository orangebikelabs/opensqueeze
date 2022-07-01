/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import okhttp3.Request;

/**
 * @author tbsandee@orangebikelabs.com
 */
abstract public class SBCredentials {

    abstract public boolean checkCredentials(long duration, TimeUnit unit) throws InterruptedException;

    @Nullable
    abstract public HttpUtils.Header getHeader(URL url);

    public void apply(HttpURLConnection connection) {
        HttpUtils.Header header = getHeader(connection.getURL());
        if (header != null) {
            connection.setRequestProperty(header.getName(), header.getValue());
        }
    }

    public void apply(Request.Builder builder, URL url) {
        HttpUtils.Header header = getHeader(url);
        if (header != null) {
            builder.header(header.getName(), header.getValue());
        }
    }

    protected boolean matches(URL url, String hostname, int port) {
        return url.getHost().equals(hostname) && url.getPort() == port;
    }
}
