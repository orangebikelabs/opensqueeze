/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.base.Charsets;
import com.orangebikelabs.orangesqueeze.common.AbsInterruptibleThreadService;
import com.orangebikelabs.orangesqueeze.common.EqualityLatch;
import com.orangebikelabs.orangesqueeze.common.JsonHelper;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.concurrent.ThreadSafe;

import okhttp3.CacheControl;

/**
 * Thread that handles writing requests to the Squeezebox streaming connection.
 *
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
public class StreamingWriter extends AbsInterruptibleThreadService {
    final static private JsonNode END = MissingNode.getInstance();
    final private LinkedBlockingQueue<JsonNode> mRequestQueue = new LinkedBlockingQueue<>();
    final private StreamingSocket mSocket;
    final private String mUserAgentHeader;
    final private String mCacheControlHeader;

    public StreamingWriter(StreamingSocket socket, String userAgentString) {
        mSocket = socket;
        mUserAgentHeader = "User-Agent: " + userAgentString;
        mCacheControlHeader = "Cache-Control: " + new CacheControl.Builder().noCache().noStore().noTransform().build();
    }

    @Override
    protected String serviceName() {
        return "StreamingWriter";
    }

    @Override
    protected void triggerShutdown() {
        super.triggerShutdown();

        mRequestQueue.clear();
        mRequestQueue.offer(END);
    }

    @Override
    protected void run() {
        try {
            OutputStream os = mSocket.getRawSocket().getOutputStream();
            EqualityLatch<Boolean> readyToWrite = mSocket.getReadyToWriteLatch();
            URL url = mSocket.getConnection().getCometUrl();
            SBCredentials creds = mSocket.getConnection().getSbContext().getConnectionCredentials();
            JsonNode node;
            while ((node = getNextRequest()) != END && isRunning()) {
                readyToWrite.await(Boolean.TRUE);

                byte[] nodeBytes = node.toString().getBytes(Charsets.UTF_8.name());

                safePrintln(os, "POST " + url.getPath() + " HTTP/1.1");
                safePrintln(os, "Host: " + url.getHost() + ":" + url.getPort());
                safePrintln(os, "Cache-Control: no-cache");
                safePrintln(os, "Content-Type: " + JsonRpc.JSON_CONTENT_TYPE);
                safePrintln(os, "Content-Length: " + nodeBytes.length);
                safePrintln(os, "Accept-Charset: " + Charsets.UTF_8.name());
                safePrintln(os, mUserAgentHeader);

                if (creds != null) {
                    HttpUtils.Header header = creds.getHeader(url);
                    if (header != null) {
                        safePrintln(os, header.getName() + ": " + header.getValue());
                    }
                }

                safePrintln(os, "");
                if (OSLog.isLoggable(Tag.NETWORK, OSLog.VERBOSE)) {
                    OSLog.v(Tag.NETWORK, "StreamingWriter::post body = " + new String(nodeBytes, Charsets.UTF_8));
                }
                os.write(nodeBytes);

                os.flush();
                readyToWrite.set(Boolean.FALSE);
            }
            os.close();
        } catch (IOException e) {
            if (isRunning()) {
                OSLog.w(Tag.NETWORK, "WriterThread IOException", e);
            }
        } catch (InterruptedException e) {
            OSLog.v(Tag.NETWORK, "WriterThread interrupted");
        } finally {
            mSocket.onWriterQuit(isRunning());
        }
    }

    public void addRequest(JsonNode request) {
        mRequestQueue.add(request);
    }

    private void safePrintln(OutputStream os, String line) throws IOException {
        if (OSLog.isLoggable(Tag.NETWORK, OSLog.VERBOSE)) {
            OSLog.v(Tag.NETWORK, "StreamingWriter::safePrintln = " + line);
        }
        os.write(line.getBytes("US-ASCII"));
        os.write("\r\n".getBytes("US-ASCII"));
    }

    protected JsonNode getNextRequest() throws InterruptedException {

        JsonNode node = mRequestQueue.take();
        if (node == END) {
            return node;
        }

        ArrayNode retval;
        if (node.isArray()) {
            retval = (ArrayNode) node;
        } else {
            retval = JsonHelper.getJsonObjectMapper().createArrayNode();
            retval.add(node);
        }
        return retval;
    }
}
