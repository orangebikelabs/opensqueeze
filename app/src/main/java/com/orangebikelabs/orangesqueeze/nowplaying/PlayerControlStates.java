/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.nowplaying;

import androidx.annotation.IdRes;
import android.util.SparseIntArray;
import android.view.View;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus.ButtonStatus;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus.PlayerButton;

/**
 * @author tsandee
 */
public class PlayerControlStates {
    static public void putViewVisibilities(PlayerStatus status, SparseIntArray destination) {
        setVisibleOrGone(destination, R.id.play_button, status.getMode() != PlayerStatus.Mode.PLAYING);
        setVisibleOrGone(destination, R.id.pause_button, status.getMode() == PlayerStatus.Mode.PLAYING);

        ButtonStatus backButtonStatus = status.getButtonStatus(PlayerButton.BACK).orNull();

        setVisibleOrGone(destination, R.id.previous_button, backButtonStatus != null && backButtonStatus.isEnabled());

        ButtonStatus nextButtonStatus = status.getButtonStatus(PlayerButton.FORWARD).orNull();
        setVisibleOrGone(destination, R.id.next_button, nextButtonStatus != null && nextButtonStatus.isEnabled());

        ButtonStatus shuffleButtonStatus = status.getButtonStatus(PlayerButton.SHUFFLE).orNull();
        boolean thumbsDownEnabled = shuffleButtonStatus != null && shuffleButtonStatus.isThumbsDown();
        setVisibleOrGone(destination, R.id.thumbsdown_button, thumbsDownEnabled);

        ButtonStatus repeatButtonStatus = status.getButtonStatus(PlayerButton.REPEAT).orNull();
        boolean thumbsUpEnabled = repeatButtonStatus != null && repeatButtonStatus.isThumbsUp();
        setVisibleOrGone(destination, R.id.thumbsup_button, thumbsUpEnabled);

        setVisibleOrGone(destination, R.id.repeat_button, repeatButtonStatus != null && repeatButtonStatus.getJiveStyle() == null);
        setVisibleOrGone(destination, R.id.shuffle_button, shuffleButtonStatus != null && shuffleButtonStatus.getJiveStyle() == null);

        destination.put(R.id.volume_button, View.VISIBLE);
    }

    static private void setVisibleOrGone(SparseIntArray array, @IdRes int id, boolean value) {
        array.put(id, value ? View.VISIBLE : View.GONE);
    }

    static private void setVisibleOrInvisible(SparseIntArray array, @IdRes int id, boolean value) {
        array.put(id, value ? View.VISIBLE : View.INVISIBLE);
    }

    public static int getCurrentViewImageLevel(PlayerStatus status, @IdRes int id) {
        int retval = -1;
        if (id == R.id.thumbsdown_button) {
            retval = status.isThumbsDownPressed() ? 1 : 0;
        } else if (id == R.id.thumbsup_button) {
            retval = status.isThumbsUpPressed() ? 1 : 0;
        } else if (id == R.id.repeat_button) {
            retval = status.getRepeatMode().ordinal();
        } else if (id == R.id.shuffle_button) {
            retval = status.getShuffleMode().ordinal();
        } else if (id == R.id.play_button
                || id == R.id.pause_button
                || id == R.id.next_button) {
            retval = 0;
        }
        return retval;
    }

    public static int getMaxViewImageLevel(@IdRes int id) {
        int retval = -1;
        if (id == R.id.thumbsdown_button) {
            retval = 2;
        } else if (id == R.id.thumbsup_button) {
            retval = 2;
        } else if (id == R.id.repeat_button) {
            retval = PlayerStatus.RepeatMode.values().length;
        } else if (id == R.id.shuffle_button) {
            retval = PlayerStatus.ShuffleMode.values().length;
        } else if (id == R.id.play_button
                || id == R.id.pause_button
                || id == R.id.next_button) {
            retval = 1;
        }
        return retval;
    }
}
