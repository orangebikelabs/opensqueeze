/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.startup;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.PendingConnection;
import com.orangebikelabs.orangesqueeze.app.SBActivity;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.event.PendingConnectionState;
import com.squareup.otto.Subscribe;

import javax.annotation.Nullable;

import androidx.activity.OnBackPressedCallback;
import androidx.core.text.HtmlCompat;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class ConnectingActivity extends SBActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBus.register(mEventReceiver);

        // if we're redirecting then don't bother with any extra init
        if (isFinishing()) {
            return;
        }

        setContentView(R.layout.connecting);

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mContext.abortPendingConnection()) {
                    launchConnectActivity();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mBus.unregister(mEventReceiver);
    }

    /**
     * make sure that we're only in this activity if connection state is disconnected or while connecting
     */
    @Override
    protected boolean isSupportedConnectionState(ConnectionInfo ci) {
        return !ci.isConnected() && mContext.isConnecting();
    }

    @Override
    protected void showConnectingDialog() {
        // don't show connecting dialog in this activity
    }

    private void launchConnectActivity() {
        if (isFinishing()) {
            // we've already launched it
            return;
        }

        startActivity(new Intent(this, ConnectActivity.class));
        finish();

        // do slide from left
        overridePendingTransition(R.anim.in_from_left, android.R.anim.fade_out);
    }

    final private Object mEventReceiver = new Object() {
        /** this event will come in once the activity is started */
        @Subscribe
        public void whenPendingConnectionChanged(PendingConnectionState event) {
            if (!event.isConnecting() && !mContext.isConnected()) {
                launchConnectActivity();
            }

            PendingConnection pending = event.getPendingConnection();
            if (pending != null) {
                TextView tv = findViewById(R.id.connecting_text);
                String message = getString(R.string.connecting_message_html, pending.getServerName());
                tv.setText(HtmlCompat.fromHtml(message, 0));
            }
        }
    };
}
