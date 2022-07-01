/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.orangebikelabs.orangesqueeze.BuildConfig;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.Constants;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.internal.Version;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * A few utility methods for using HTTP in our special ways.
 *
 * @author tbsandee@orangebikelabs.com
 */
@SuppressWarnings("deprecation")
public class HttpUtils {
    static public class Header {
        final private String mName;
        final private String mValue;

        public Header(String name, String value) {
            mName = name;
            mValue = value;
        }

        public String getName() {
            return mName;
        }

        public String getValue() {
            return mValue;
        }
    }

    final static public char CR = '\r';
    final static public char LF = '\n';

    static private OkHttpClient sHttpClient;

    static {
        sHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Constants.CONNECTION_TIMEOUT, Constants.TIME_UNITS)
                .readTimeout(Constants.READ_TIMEOUT, Constants.TIME_UNITS)
                .writeTimeout(Constants.WRITE_TIMEOUT, Constants.TIME_UNITS)
                .build();
    }

    @SuppressWarnings("deprecation")
    @Nonnull
    static public HttpURLConnection open(URL url, boolean useGlobalCookies) {
        okhttp3.OkUrlFactory factory = new okhttp3.OkUrlFactory(newHttpClient());
        HttpURLConnection connection = factory.open(url);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", getUserAgent());
        connection.setRequestProperty("Cache-Control", sCacheControl.toString());
        if (useGlobalCookies) {
            Header cookie = getCookieHeader(url).orNull();
            if (cookie != null) {
                OSLog.v(OSLog.Tag.NETWORK, "Setting cookie: " + cookie.getValue());
                connection.setRequestProperty(cookie.getName(), cookie.getValue());
            }
        } else {
            OSLog.i(OSLog.Tag.NETWORK, "Not using global cookie for request url: " + url);
        }
        return connection;
    }

    final private static CacheControl sCacheControl = new CacheControl.Builder().noCache().noStore().noTransform().build();

    /**
     * initialize the http client for the supplied context
     */
    @Nonnull
    static public OkHttpClient newHttpClient() {
        OkHttpClient retval = sHttpClient;
        if (OSLog.isLoggable(OSLog.Tag.NETWORK, OSLog.VERBOSE)) {
            retval = retval.newBuilder()
                    .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                    .build();
        } else if (OSLog.isLoggable(OSLog.Tag.NETWORK, OSLog.DEBUG)) {
            retval = retval.newBuilder()
                    .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                    .build();
        }
        return retval;
    }

    @Nonnull
    static public Request.Builder newRequestBuilder(URL url, boolean useGlobalCookies) {
        Request.Builder retval = new Request.Builder();
        retval.url(url);
        if (useGlobalCookies) {
            Header cookie = getCookieHeader(url).orNull();
            if (cookie != null) {
                OSLog.v(OSLog.Tag.NETWORK, "Setting cookie: " + cookie.getValue());
                retval.header(cookie.getName(), cookie.getValue());
            } else {
                retval.removeHeader("Cookie");
                retval.removeHeader("Cookie2");
                OSLog.v(OSLog.Tag.NETWORK, "Using empty cookie");
            }
        } else {
            OSLog.i(OSLog.Tag.NETWORK, "Not using global cookie for request url: " + url);
        }
        retval.header("User-Agent", getUserAgent());
        retval.cacheControl(sCacheControl);
        return retval;
    }

    @Nonnull
    static public String getUserAgent() {
        return "OpenSqueeze/" + BuildConfig.VERSION_NAME + " " + Version.userAgent();
    }

    @Nonnull
    static public URL getJSONUrl(ConnectionInfo ci) throws MalformedURLException {
        return new URL("http://" + ci.getServerHost() + ":" + ci.getServerPort() + "/jsonrpc.js");
    }

    @Nonnull
    static public URL getCometUrl(ConnectionInfo ci) throws MalformedURLException {
        if (ci.isSqueezeNetwork()) {
            return new URL("http://jive.squeezenetwork.com:9000/cometd");
        } else {
            return new URL("http://" + ci.getServerHost() + ":" + ci.getServerPort() + "/cometd");
        }
    }

    final static private ConcurrentHashMap<String, ConcurrentHashMap<String, CookieValue>> sCookies = new ConcurrentHashMap<>();

    static private class CookieValue {
        final private String mCookieValue;
        final private long mAge;

        public CookieValue(String cookieValue, long age) {
            mCookieValue = cookieValue;
            mAge = age;
        }
    }

    /**
     * strip off any path and query components of URL
     */
    @Nonnull
    static private String getMatchURI(URL url) {
        StringBuilder retval = new StringBuilder(url.getProtocol());
        retval.append("://");
        retval.append(url.getHost());
        int port = url.getPort();
        if (port == -1) {
            port = 80;
        }
        retval.append(':');
        retval.append(port);
        return retval.toString();
    }

    @Nonnull
    static public Optional<Header> getCookieHeader(URL url) {
        Header retval = null;
        String cookieValue = getCookieValue(url);
        if (cookieValue != null) {
            retval = new Header("Cookie", cookieValue);
        }
        return Optional.fromNullable(retval);
    }

    @Nullable
    static private String getCookieValue(URL url) {
        String retval = null;
        try {
            String uri = getMatchURI(url);
            if (uri.contains("squeezenetwork.com")) {
                uri = "http://www.squeezenetwork.com:80";
            }

            Map<String, CookieValue> cookies = sCookies.get(uri);
            if (cookies != null) {
                StringBuilder builder = new StringBuilder();
                for (Map.Entry<String, CookieValue> entry : cookies.entrySet()) {
                    if (builder.length() > 0) {
                        builder.append(';');
                    }
                    builder.append(URLEncoder.encode(entry.getKey(), Charsets.UTF_8.name()));
                    builder.append('=');
                    builder.append(URLEncoder.encode(entry.getValue().mCookieValue, Charsets.UTF_8.name()));
                }
                retval = builder.toString();
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return retval;
    }

    static public void addCookie(URL cookieUrl, String cookieName, String value, long age) {
        ConcurrentHashMap<String, CookieValue> cookies = getCookies(getMatchURI(cookieUrl));
        cookies.put(cookieName, new CookieValue(value, age));
    }

    @Nonnull
    static private ConcurrentHashMap<String, CookieValue> getCookies(String cookieUri) {

        ConcurrentHashMap<String, CookieValue> retval = sCookies.get(cookieUri);
        if (retval == null) {
            sCookies.putIfAbsent(cookieUri, new ConcurrentHashMap<>());
            retval = sCookies.get(cookieUri);
        }
        return OSAssert.assertNotNull(retval, "can't be null");
    }

    public static void removeCookie(URL cookieUri, String cookieName) {
        Map<String, CookieValue> cookies = getCookies(getMatchURI(cookieUri));
        cookies.remove(cookieName);
    }
}
