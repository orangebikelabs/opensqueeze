/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.net;

import com.orangebikelabs.orangesqueeze.common.SBRequestException;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class JsonRpcException extends SBRequestException {
    private static final long serialVersionUID = 6939787029537175891L;

    final private int mResponseCode;

    public JsonRpcException(int responseCode) {
        super("HTTP error code " + responseCode);

        mResponseCode = responseCode;
    }

    public JsonRpcException(Throwable cause) {
        super(cause);

        mResponseCode = 0;
    }

    public int getResponseCode() {
        return mResponseCode;
    }
}
