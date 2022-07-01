/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import android.content.Context;
import android.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.Reporting;
import com.orangebikelabs.orangesqueeze.common.SBRequestException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class SqueezeNetworkCredentials extends SBCredentials {
    @Nonnull
    final private String mHostname;
    final private int mPort;
    @Nonnull
    final private URL mCookieUrl;

    @Nonnull
    final private Context mContext;

    private String mUsername;
    private char[] mPassword;
    private String mSid;

    public SqueezeNetworkCredentials(Context context, String hostname, int port) throws SBRequestException {
        try {
            mContext = context;
            mHostname = hostname;
            mPort = port;
            mCookieUrl = new URL("http://" + mHostname + ":" + mPort + "/");
        } catch (MalformedURLException e) {
            throw SBRequestException.wrap(e);
        }
    }

    public void set(String username, char[] password) {
        mUsername = username;
        mPassword = password.clone();
    }

    public String getSid() {
        return mSid;
    }

    @Override
    @Nullable
    public HttpUtils.Header getHeader(URL url) {
        return null;
    }

    @Override
    public boolean checkCredentials(long duration, TimeUnit unit) throws InterruptedException {
        boolean retval = false;
        String passwordHash = hashPassword(mPassword);

        String sid = getNewSid(mUsername, passwordHash, duration, unit);
        if (sid != null) {
            HttpUtils.addCookie(mCookieUrl, "sdi_squeezenetwork_session", sid, 30L * 24L * 60L * 60L);
            mSid = sid;
            retval = true;
        }
        return retval;
    }

    @Nullable
    private String getNewSid(String username, String passwordHash, long timeout, TimeUnit units) throws InterruptedException {
        String sid = null;
        try {
            URL url = constructUrl(username, passwordHash);
            JsonRpc rpc = new JsonRpc();
            rpc.setTimeout(timeout, units);
            JsonRpc.Result result = rpc.execute(url, null);
            try {
                JsonNode node = result.readValueAsTree();
                if (node.isObject()) {
                    sid = JsonHelper.getString(node, "sid", null);
                }
                if (sid != null && (sid.equals("null") || sid.equals(""))) {
                    sid = null;
                }
            } finally {
                result.close();
            }
        } catch (JsonRpcException | IOException e) {
            OSLog.w("Connecting to SqueezeNetwork", e);
        } catch (NoSuchAlgorithmException e) {
            Reporting.report(e);
        }
        return sid;
    }

    @Nonnull
    private String getAuthenticationUri() {
        return "http://" + mHostname + ":" + mPort + "/api/v1/login";
    }

    @Nonnull
    private String hashPassword(char[] password) {
        try {
            return sha1_base64(new String(password));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Nonnull
    private URL constructUrl(String username, String passwordHash) throws NoSuchAlgorithmException, UnsupportedEncodingException, MalformedURLException {
        String v = "sc7.5.4-sn";
        long time = System.currentTimeMillis() / 1000;
        String t = Long.toString(time);
        String a = sha1_base64(passwordHash + t);

        String uri = getAuthenticationUri();
        uri += "?v=" + URLEncoder.encode(v, Charsets.UTF_8.name()) + "&";
        uri += "u=" + URLEncoder.encode(username, Charsets.UTF_8.name()) + "&";
        uri += "t=" + URLEncoder.encode(t, Charsets.UTF_8.name()) + "&";
        uri += "a=" + URLEncoder.encode(a, Charsets.UTF_8.name());
        return new URL(uri);
    }

    @Nonnull
    private static String sha1_base64(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        return Base64.encodeToString(sha1(str), Base64.NO_WRAP | Base64.NO_PADDING);
    }

    @Nonnull
    private static byte[] sha1(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest digest = MessageDigest.getInstance("SHA1");
        return digest.digest(str.getBytes(Charsets.UTF_8.name()));
    }
}
