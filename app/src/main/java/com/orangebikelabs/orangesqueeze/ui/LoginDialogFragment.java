/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.PendingConnection;
import com.orangebikelabs.orangesqueeze.app.SBDialogFragment;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.EncryptionTools;
import com.orangebikelabs.orangesqueeze.common.OSExecutors;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.ServerType;
import com.orangebikelabs.orangesqueeze.common.event.PendingConnectionState;
import com.orangebikelabs.orangesqueeze.database.DatabaseAccess;
import com.orangebikelabs.orangesqueeze.databinding.LoginBinding;
import com.orangebikelabs.orangesqueeze.database.Server;
import com.orangebikelabs.orangesqueeze.startup.ConnectFragment;
import com.squareup.otto.Subscribe;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import androidx.appcompat.app.AlertDialog;
/**
 * @author tbsandee@orangebikelabs.com
 */
public class LoginDialogFragment extends SBDialogFragment {
    final static private String ARG_SERVER_ID = "ServerID";
    final static private String ARG_SERVER_NAME = "ServerName";

    public static LoginDialogFragment newInstance(ConnectFragment fragment, long serverId, String serverName) {
        Bundle args = new Bundle();
        args.putLong(ARG_SERVER_ID, serverId);
        args.putString(ARG_SERVER_NAME, serverName);

        LoginDialogFragment retval = new LoginDialogFragment();
        retval.setArguments(args);
        retval.setTargetFragment(fragment, 0);
        return retval;
    }

    private long mServerId;
    private String mServerName;
    private ServerType mServerType;
    private String mServerHost;
    private int mServerPort;

    // should we block clicks from being processed, set to true during
    // processing
    private boolean mBlockClicks;

    private LoginBinding mBinding;

    private Button mConnectButton;

    @Nullable
    private PendingConnection mCurrentPendingConnection;

    public LoginDialogFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        OSAssert.assertNotNull(args, "require arguments");

        mServerId = args.getLong(ARG_SERVER_ID);
        mServerName = args.getString(ARG_SERVER_NAME);
        mBus.register(mEventReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mBus.unregister(mEventReceiver);
    }

    @Override
    @Nonnull
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(getString(R.string.login_title, mServerName));
        builder.setCancelable(true);

        @SuppressLint("InflateParams") View rootView = getLayoutInflater().inflate(R.layout.login, null, false);
        OSAssert.assertNotNull(rootView, "root view hierarchy must inflate properly");

        mBinding = LoginBinding.bind(rootView);
        mBinding.forgotPassword.setOnClickListener(v -> goForgotPassword());
        mBinding.password.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    mConnectListener.onClick(v);
                }
                // absorb action_up as well
                return true;
            } else {
                return false;
            }
        });
        builder.setView(rootView);
        builder.setPositiveButton(R.string.connect_button, null);
        builder.setNegativeButton(R.string.cancel, (dialog, whichButton) -> {
            // nothing to do, let the dialog cancel
        });
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();

        // the DialogFragment has .show() called in onStart(), and we need to override the listener at that time
        AlertDialog retval = (AlertDialog) OSAssert.assertNotNull(getDialog(), "Can't be null");

        // override the connect button
        mConnectButton = retval.getButton(DialogInterface.BUTTON_POSITIVE);
        OSAssert.assertNotNull(mConnectButton, "connect button must exist");

        mConnectButton.setOnClickListener(mConnectListener);

        // run this database-driven task in the background
        UsernamePasswordFillTask task = new UsernamePasswordFillTask();
        task.execute();
    }

    private void goForgotPassword() {
        if (mServerType == null) {
            return;
        }
        String url;
        if (mServerType == ServerType.SQUEEZENETWORK) {
            url = "https://mysqueezebox.com/user/login";
        } else {
            url = "http://" + mServerHost + ":" + mServerPort + "/settings/index.html";
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
    }

    /**
     * called when add button is clicked
     */
    final private OnClickListener mConnectListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mBlockClicks) {
                return;
            }

            mBlockClicks = true;
            mConnectButton.setEnabled(false);

            String username = getEditViewText(mBinding.username, "");
            String password = getEditViewText(mBinding.password, "");

            if (mCurrentPendingConnection != null) {
                ListenableFuture<Boolean> future = mCurrentPendingConnection.setConnectionCredentials(username, password);
                Futures.addCallback(future, mAuthenticationCallback, OSExecutors.getMainThreadExecutor());
            }
        }
    };

    /**
     * called when response is received from service about authentication
     */
    final private FutureCallback<Boolean> mAuthenticationCallback = new FutureCallback<Boolean>() {
        @Override
        public void onFailure(Throwable e) {
            OSLog.w(e.getMessage(), e);
            onSuccess(Boolean.FALSE);
        }

        @Override
        public void onSuccess(@Nullable Boolean success) {
            OSAssert.assertNotNull(success, "can't be null");

            if (!isAdded()) {
                return;
            }

            if (success) {
                // dismiss dialog
                dismissAllowingStateLoss();
            } else {
                // reenable button
                mBlockClicks = false;
                mConnectButton.setEnabled(true);
            }

            ConnectFragment fragment = (ConnectFragment) getTargetFragment();
            OSAssert.assertNotNull(fragment, "fragment must be set");

            fragment.onLoginDialogResult(success, mServerId, mServerName);
        }
    };

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenPendingConnectionStateChanges(PendingConnectionState state) {
            // ensures we have the current pending connection state at any given moment
            mCurrentPendingConnection = state.getPendingConnection();
        }
    };

    /**
     * access the database and populates the username/password fields properly in the background
     */
    @SuppressLint("StaticFieldLeak")
    class UsernamePasswordFillTask extends AsyncTask<Void, Void, Boolean> {
        volatile String mUsername;
        volatile String mPassword;

        @Override
        protected Boolean doInBackground(Void... params) {
            if (!isAdded()) {
                // dialog is closed already
                return Boolean.FALSE;
            }

            Server server = DatabaseAccess.getInstance(mContext.getApplicationContext())
                    .getServerQueries()
                    .lookupById(mServerId).executeAsOneOrNull();
            if (server == null) {
                return Boolean.FALSE;
            }
            mUsername = server.getServerusername();

            String key = server.getServerkey();
            String encryptedPassword = server.getServerpassword();
            mServerType = server.getServertype();
            mServerHost = server.getServerhost();
            mServerPort = server.getServerport();

            mPassword = "";
            if (encryptedPassword != null && key != null) {
                try {
                    byte[] decrypted = EncryptionTools.decrypt(key, encryptedPassword);
                    mPassword = new String(decrypted, "UTF-8");
                } catch (GeneralSecurityException | UnsupportedEncodingException e) {
                    OSLog.e("encryption error", e);
                }
            }
            return Boolean.TRUE;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            // if the fragment goes away while this is running, clean exit
            if (!isAdded()) {
                return;
            }

            if (!result) {
                dismissAllowingStateLoss();
                return;
            }

            mBinding.username.setText(Strings.nullToEmpty(mUsername));
            mBinding.password.setText(Strings.nullToEmpty(mPassword));
        }
    }

    static private String getEditViewText(TextView view, @Nullable String defaultValue) {
        CharSequence val = view.getText();
        if (val == null) {
            return defaultValue;
        }
        return val.toString();
    }
}
