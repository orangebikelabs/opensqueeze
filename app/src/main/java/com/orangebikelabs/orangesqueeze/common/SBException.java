/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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
