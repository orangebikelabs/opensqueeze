/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

/**
 * @author tsandee
 */
abstract public class SBException extends Exception {


    public SBException() {
    }

    public SBException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public SBException(String detailMessage) {
        super(detailMessage);
    }

    public SBException(Throwable throwable) {
        super(throwable);
    }
}
