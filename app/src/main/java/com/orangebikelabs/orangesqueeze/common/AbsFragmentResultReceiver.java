/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.common;

import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.FutureCallback;

import java.lang.ref.WeakReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A result receiver that will only deliver the onSuccess if the fragment is still added. This allows background results to trickle in but
 * not cause a crash.
 * <p/>
 * This receiver holds the fragment reference weakly, so if the fragment is removed then the GC can continue normally.
 *
 * @author tbsandee@orangebikelabs.com
 */
public abstract class AbsFragmentResultReceiver<F extends Fragment> implements FutureCallback<SBResult> {
    /**
     * hold onto fragment with weak reference
     */
    @Nonnull
    final private WeakReference<F> mTarget;

    public AbsFragmentResultReceiver(F target) {
        mTarget = new WeakReference<>(target);
    }

    @Override
    final public void onSuccess(@Nullable final SBResult result) {
        F fragment = shouldNotifyContinue();
        if (fragment != null && result != null) {
            onEventualSuccess(fragment, result);
        }
    }

    @Override
    final public void onFailure(@Nullable Throwable e) {
        F fragment = shouldNotifyContinue();
        if (fragment != null) {
            onEventualError(fragment, e);
        }
    }

    abstract public void onEventualSuccess(F fragment, SBResult result);

    public void onEventualError(F fragment, @Nullable Throwable e) {
        OSLog.w(e == null ? "error" : e.getMessage(), e);
    }

    @Nullable
    protected F shouldNotifyContinue() {
        F target = mTarget.get();
        if (target != null && target.isAdded() && !target.isRemoving()) {
            return target;
        } else {
            return null;
        }
    }
}
