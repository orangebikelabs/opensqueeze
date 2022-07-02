/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.SBActivity;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.event.PlayerListChangedEvent;
import com.orangebikelabs.orangesqueeze.startup.SwitchServerActivity;
import com.squareup.otto.Subscribe;

import javax.annotation.Nullable;

import androidx.core.text.HtmlCompat;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class NoPlayersActivity extends SBActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.noplayers);

        ImageButton connectButton = findViewById(R.id.choose_new_server_button);
        connectButton.setOnClickListener(v -> startActivity(new Intent(NoPlayersActivity.this, SwitchServerActivity.class)));

        mBus.register(mEventReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBus.unregister(mEventReceiver);
    }

    @Override
    protected boolean isSupportedConnectionState(ConnectionInfo ci) {
        return ci.isConnected() && mContext.getServerStatus().getAvailablePlayerIds().isEmpty();
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenPlayerListChanges(PlayerListChangedEvent event) {
            if (!event.getPlayerStatusList().isEmpty() && !isFinishing()) {
                launchMainActivity();
                finish();

                // don't animate this
                overridePendingTransition(0, 0);
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();

        // because connection state can change any time, we reset this when
        // onStart() is called

        if (mContext.isConnected()) {
            TextView tv = findViewById(R.id.noplayer_server_label);
            String message = getString(R.string.no_player_available_label_html, mContext.getConnectionInfo().getServerName());
            tv.setText(HtmlCompat.fromHtml(message, 0));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
