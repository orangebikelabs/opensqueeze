/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common;

/**
 * @author tsandee
 */
public interface Bus {

    /**
     * register from any thread
     */
    void register(Object o);

    /**
     * unregister from any thread
     */
    void unregister(Object o);

    /**
     * post from any thread
     */
    void post(Object event);

    /**
     * post from MAIN thread and make sure it is synchronous
     */
    void postFromMain(Object event);
}
