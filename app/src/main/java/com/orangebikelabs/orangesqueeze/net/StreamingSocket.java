/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.Service.State;
import com.orangebikelabs.orangesqueeze.common.EqualityLatch;
import com.orangebikelabs.orangesqueeze.net.StreamingReader.RestartException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import javax.annotation.concurrent.ThreadSafe;

/**
 * @author tbsandee@orangebikelabs.com
 */
@ThreadSafe
public class StreamingSocket {
    final private EqualityLatch<Boolean> mReadyToWrite = EqualityLatch.create(Boolean.FALSE);

    final private StreamingReader mReaderThread;
    final private StreamingWriter mWriterThread;
    final private Socket mSocket;
    final private StreamingConnection mConnection;

    public StreamingSocket(StreamingConnection connection) {
        mConnection = connection;
        mSocket = new Socket();

        String userAgentString = HttpUtils.getUserAgent();
        mReaderThread = new StreamingReader(this);
        mWriterThread = new StreamingWriter(this, userAgentString);
    }

    public void connect() throws IOException {
        SocketAddress address = new InetSocketAddress(mConnection.getCometUrl().getHost(), mConnection.getCometUrl().getPort());
        mSocket.connect(address);

        mReaderThread.startAsync();
        mWriterThread.startAsync();

        mReadyToWrite.set(Boolean.TRUE);
    }

    public EqualityLatch<Boolean> getReadyToWriteLatch() {
        return mReadyToWrite;
    }

    public Socket getRawSocket() {
        return mSocket;
    }

    public StreamingConnection getConnection() {
        return mConnection;
    }

    public boolean isRunning() {
        State readerState = mReaderThread.state();
        return mSocket.isConnected() && (readerState == State.RUNNING || readerState == State.STARTING);
    }

    public void addDoneSendingRequest() {
        // no implementation
    }

    public void close() {
        mReaderThread.stopAsync();
        mWriterThread.stopAsync();

        try {
            mSocket.close();
        } catch (IOException e) {
            // ignore
        }
    }

    public void addRequest(JsonNode request) {
        mWriterThread.addRequest(request);
    }

    public void onResponseReceived(JsonNode node) throws RestartException {
        if (mConnection.isMetaConnectMessage(node)) {
            if (!mConnection.isSuccessful(node)) {
                // restart immediately and terminate this task
                throw new RestartException("Unsuccessful connect message, restarting service");
            }
        } else {
            mConnection.onResponseReceived(node);
        }
    }

    public void onReaderQuit(boolean error) {
        mConnection.quitMain(error);
    }

    public void onWriterQuit(boolean error) {
        mConnection.quitMain(error);
    }
}
