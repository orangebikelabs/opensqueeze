/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
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
