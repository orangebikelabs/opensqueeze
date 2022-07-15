/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;

import android.text.Html;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.cache.CacheServiceProvider;
import com.orangebikelabs.orangesqueeze.common.BusProvider;
import com.orangebikelabs.orangesqueeze.common.BusProvider.ScopedBus;
import com.orangebikelabs.orangesqueeze.common.ConnectionInfo;
import com.orangebikelabs.orangesqueeze.common.MenuTools;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.common.OSLog;
import com.orangebikelabs.orangesqueeze.common.OSLog.Tag;
import com.orangebikelabs.orangesqueeze.common.PlayerId;
import com.orangebikelabs.orangesqueeze.common.PlayerStatus;
import com.orangebikelabs.orangesqueeze.common.SBContext;
import com.orangebikelabs.orangesqueeze.common.SBContextProvider;
import com.orangebikelabs.orangesqueeze.common.SBPreferences;
import com.orangebikelabs.orangesqueeze.common.ThemeSelector;
import com.orangebikelabs.orangesqueeze.common.event.ActivePlayerChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.AppPreferenceChangeEvent;
import com.orangebikelabs.orangesqueeze.common.event.ConnectionStateChangedEvent;
import com.orangebikelabs.orangesqueeze.common.event.ConnectivityChangeEvent;
import com.orangebikelabs.orangesqueeze.common.event.PendingConnectionState;
import com.orangebikelabs.orangesqueeze.common.event.PlayerListChangedEvent;
import com.orangebikelabs.orangesqueeze.net.DeviceConnectivity;
import com.orangebikelabs.orangesqueeze.players.NoPlayersActivity;
import com.orangebikelabs.orangesqueeze.startup.ConnectActivity;
import com.orangebikelabs.orangesqueeze.startup.ConnectingActivity;
import com.orangebikelabs.orangesqueeze.startup.SwitchServerActivity;
import com.orangebikelabs.orangesqueeze.ui.MainActivity;
import com.orangebikelabs.orangesqueeze.ui.MainPreferenceActivity;
import com.orangebikelabs.orangesqueeze.ui.MaintenanceDialogFragment;
import com.orangebikelabs.orangesqueeze.ui.VolumeFragment;
import com.squareup.otto.Subscribe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import androidx.core.app.ActivityCompat;
import androidx.core.text.HtmlCompat;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Activity that adds lifecycle callbacks for connection to the Squeezebox server.
 *
 * @author tbsandee@orangebikelabs.com
 */
abstract public class SBActivity extends AppCompatActivity {
    public static int getInstanceCount() {
        return sInstanceCount;
    }

    static public boolean sShouldAutoStart = true;

    static private int sInstanceCount = 0;

    /**
     * track a deferred player toast at startup to make sure we get one when the system starts up
     */
    @Nullable
    static private PlayerStatus sDeferredPlayerToast;

    @Nonnull
    final protected ScopedBus mBus = BusProvider.newScopedInstance();

    final private Object mLock = new Object();

    @Nonnull
    protected SBContext mContext = SBContextProvider.uninitialized();

    @Nullable
    private ProgressDialog mConnectingDialog;

    @Nullable
    private ProgressDialog mConnectivityDialog;

    @Nullable
    protected PendingConnection mPendingConnection;

    final private ThemeSelectorHelper mThemeSelectorHelper = new ThemeSelectorHelper();

    protected boolean mNeedRestartActivity;

    /**
     * is this a start from scratch or a return to the activity via onRestart()?
     */
    private boolean mCleanStart;

    /**
     * allows simple suppression of duplicate player change toasts at startup, and other times
     */
    @Nullable
    private PlayerId mLastShownPlayerToast;

    @Nullable
    private Disposable mTokenDisposable;

    /**
     * whether or not the activity is started
     */
    protected boolean mStarted;

    final protected ServerConnectionServiceHelper mServerConnectionServiceHelper = new ServerConnectionServiceHelper(true);

    @Override
    protected void attachBaseContext(Context base) {
        Context newBase = LocaleHelper.INSTANCE.onAttach(base);
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        logVerbose("onCreate");
        OSLog.TimingLoggerCompat timing = Tag.TIMING.newTimingLogger("SBActivity::onCreate");

        // we lazy-init the SBContext, but now it's actually required
        mContext = SBContextProvider.initializeAndGet(this);

        // autoconnect ASAP, also done in onStart()
        checkAutoConnect();

        timing.addSplit("before super.onCreate()");

        // theme setup must happen before super.onCreate() is called
        ThemeSelector newThemeSelector = mThemeSelectorHelper.getThemeDefinition();
        OSAssert.assertNotNull(newThemeSelector, "Expected theme definition to be non-null");
        setTheme(newThemeSelector.getRid());

        super.onCreate(savedInstanceState);

        mCleanStart = (savedInstanceState == null);

        BusProvider.getInstance().register(mEventReceiver);
        mBus.register(mActivityLifecycleEventReceiver);

        timing.close();

        mTokenDisposable = MediaSessionHelper.Companion.getTokenSubject()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(token -> {
                    MediaControllerCompat controller = new MediaControllerCompat(this, token);
                    MediaControllerCompat.setMediaController(this, controller);
                });

        sInstanceCount++;
    }

    @Override
    protected void onDestroy() {
        logVerbose("onDestroy");
        sInstanceCount--;
        super.onDestroy();

        if (mTokenDisposable != null) {
            mTokenDisposable.dispose();
            mTokenDisposable = null;
        }

        mBus.unregister(mActivityLifecycleEventReceiver);
        BusProvider.getInstance().unregister(mEventReceiver);
    }

    @Override
    protected void onStart() {
        logVerbose("onStart");

        super.onStart();

        checkAutoConnect();

        mServerConnectionServiceHelper.onStart();

        mStarted = true;

        mBus.start();

        mContext.onStart(this);

        if (mCleanStart) {
            mCleanStart = false;
        }
        setWindowFlags();

        // show deferred player toast when the UI is ready
        if (sDeferredPlayerToast != null) {
            PlayerStatus status = sDeferredPlayerToast;
            sDeferredPlayerToast = null;

            showPlayerToast(status);
        }
    }

    @Override
    protected void onStop() {
        logVerbose("onStop");
        mStarted = false;

        mContext.onStop(this);

        mBus.stop();

        dismissConnectivityDialog();
        dismissConnectingDialog();

        mServerConnectionServiceHelper.onStop();

        super.onStop();
    }

    protected View getContentView() {
        return findViewById(android.R.id.content);
    }

    @Override
    protected void onRestart() {
        logVerbose("onRestart");
        super.onRestart();

        // when restarting the activity, check to see if we need a hard reset
        if (mNeedRestartActivity) {
            mNeedRestartActivity = false;

            onRestartActivity();
        }
    }

    /**
     * subclasses override this
     */
    protected boolean isSupportedConnectionState(ConnectionInfo ci) {
        return ci.isConnected() && !mContext.getServerStatus().getAvailablePlayerIds().isEmpty();
    }

    private void checkAutoConnect() {
        // do our one-time autostart
        if (sShouldAutoStart) {
            sShouldAutoStart = false;
            mContext.startAutoConnect();
        }
    }

    protected void onRestartActivity() {
        ActivityCompat.recreate(this);
    }

    /**
     * conditionally overrides the behavior of the volume and other media buttons
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!mStarted || isFinishing()) {
            return false;
        }

        boolean handled = false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD: {
                MediaControllerCompat controller = MediaControllerCompat.getMediaController(this);
                if (controller != null) {
                    controller.dispatchMediaButtonEvent(event);
                    handled = true;
                }
            }
        }
        if (!handled) {
            handled = handleVolumeKeycodes(keyCode);
        }

        if (!handled) {
            handled = super.onKeyDown(keyCode, event);
        }

        return handled;
    }

    private boolean handleVolumeKeycodes(int keyCode) {
        boolean handled = false;

        // is it even in the right class of events?
        if (VolumeFragment.isControlKeyCode(keyCode) && !SBPreferences.get().shouldUseVolumeIntegration() && mContext.isConnected()) {
            handled = true;

            // we have, so now check the preferences and process appropriately
            PlayerId playerId = mContext.getPlayerId();
            if (playerId != null) {
                // first see if the volume control is already open
                VolumeFragment fragment = (VolumeFragment) getSupportFragmentManager().findFragmentByTag(VolumeFragment.TAG);
                if (fragment == null) {
                    // show it otherwise
                    fragment = VolumeFragment.newInstance(playerId, true);
                    fragment.show(getSupportFragmentManager(), VolumeFragment.TAG);
                } else {
                    // pass commands to the control
                    // these won't be called if the volumefragment is in a
                    // true dialog, but if it's added as a fragment
                    // then these will work. similar processing exists in
                    // VolumeFragment as well.
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_VOLUME_DOWN:
                            fragment.controlChangeVolumeSmallDown();
                            break;
                        case KeyEvent.KEYCODE_VOLUME_UP:
                            fragment.controlChangeVolumeSmallUp();
                            break;
                        default:
                            handled = false;
                            break;
                    }
                }
            }
        }
        return handled;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        CacheServiceProvider.get().triggerReleaseMemory();
    }

    /**
     * create all common menu items
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.common, menu);

        // Associate searchable configuration with the SearchView
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        if (searchItem != null) {

            boolean visible = false;
            searchItem.setActionView(R.layout.searchview);

            SearchView searchView = (SearchView) searchItem.getActionView();
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            if (searchManager != null) {
                SearchableInfo si = searchManager.getSearchableInfo(getComponentName());
                searchView.setSearchableInfo(si);
                searchView.setIconifiedByDefault(false);
                visible = true;
            }
            searchItem.setVisible(visible);
        }
        return true;
    }

    /**
     * handle common menu items like search, disconnect, preferences and about
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_switchserver) {
            startActivity(new Intent(this, SwitchServerActivity.class));
            return true;
        } else if (item.getItemId() == R.id.menu_preferences) {
            startActivity(new Intent(this, MainPreferenceActivity.class));
            return true;
        } else if (item.getItemId() == R.id.menu_about) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.about_title)
                    .setMessage(R.string.about_message)
                    .setIcon(R.drawable.about_icon_selected)
                    .setPositiveButton(R.string.ok,
                            (dlg, which) -> {
                                dlg.dismiss();
                            })
                    .setCancelable(true);
            builder.create().show();
            return true;
        } else if (item.getItemId() == R.id.menu_maintenance) {
            MaintenanceDialogFragment fragment = MaintenanceDialogFragment.newInstance();
            fragment.show(getSupportFragmentManager(), "maintenance");
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        boolean connected = mContext.isConnected();
        // treat it as if we're disconnected for this activity
        if (this instanceof SwitchServerActivity) {
            connected = false;
        }

        MenuTools.setVisible(menu, R.id.menu_search, connected && !mContext.getServerStatus().getAvailablePlayerIds().isEmpty());
        MenuTools.setEnabled(menu, R.id.menu_switchserver, connected);
        MenuTools.setVisible(menu, R.id.menu_players, connected);
        MenuTools.setVisible(menu, R.id.menu_maintenance, connected);

        return true;
    }

    protected void setWindowFlags() {
        Window window = getWindow();
        if (SBPreferences.get().isKeepScreenOnEnabled()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    protected void showPlayerToast(PlayerStatus status) {
        OSAssert.assertMainThread();
        if (!mStarted || isFinishing() || !allowToastDisplay()) {
            sDeferredPlayerToast = status;
            return;
        }

        sDeferredPlayerToast = null;
        if (!status.getId().equals(mLastShownPlayerToast)) {
            mLastShownPlayerToast = status.getId();
            String text = getString(R.string.change_player_notification_html, Html.escapeHtml(status.getName()));
            Toast.makeText(this, HtmlCompat.fromHtml(text, 0), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    protected void dismissConnectingDialog() {
        OSAssert.assertMainThread();
        if (mConnectingDialog != null) {
            mConnectingDialog.dismiss();
            mConnectingDialog = null;
        }
    }

    protected void dismissConnectivityDialog() {
        OSAssert.assertMainThread();
        if (mConnectivityDialog != null) {
            mConnectivityDialog.dismiss();
            mConnectivityDialog = null;
        }
    }

    protected void showConnectingDialog() {
        dismissConnectivityDialog();

        logVerbose("showConnectingDialog");
        OSAssert.assertMainThread();
        // is it already showing or is the pendingconnection not available yet?
        if (mConnectingDialog != null || mPendingConnection == null) {
            return;
        }

        String serverName = mPendingConnection.getServerName();

        mConnectingDialog = ProgressDialog.show(this,
                getString(R.string.connecting_title),
                getString(R.string.connecting_message_html, Html.escapeHtml(serverName)),
                true,
                true, dialog -> mContext.abortPendingConnection());
    }

    protected void showConnectivityDialog() {
        dismissConnectingDialog();

        logVerbose("showConnectivityDialog");
        OSAssert.assertMainThread();
        // is it already showing
        if (mConnectivityDialog != null) {
            return;
        }

        mConnectivityDialog = ProgressDialog.show(this,
                getString(R.string.awaitingnetwork_title),
                getString(R.string.awaitingnetwork_message),
                true,
                true, dialog -> {
                    finish();
                    SBContextProvider.get().disconnect();
                    startActivity(new Intent(SBActivity.this, ConnectActivity.class));
                });
    }

    /**
     * returns whether or normal processing should continue
     */
    protected boolean checkConnectionState() {
        ConnectionInfo ci = mContext.getConnectionInfo();
        boolean ok;

        logVerbose("checkConnectionState %s", ci);

        if (isFinishing()) {
            logVerbose("checkConnectionState finishing " + ci);
            return false;
        }

        if (!DeviceConnectivity.Companion.getInstance().getDeviceConnectivity()) {
            ok = true;
            logVerbose("no device connectivity");
            showConnectivityDialog();
        } else {
            // yes, connectivity!
            dismissConnectivityDialog();

            if (isSupportedConnectionState(ci)) {
                ok = true;
                logVerbose("checkConnectionState SUPPORTED " + ci);
                if (mContext.isConnecting()) {
                    showConnectingDialog();
                } else {
                    dismissConnectingDialog();
                }
            } else {
                ok = false;
                if (ci.isConnected()) {
                    if (mContext.getServerStatus().getAvailablePlayerIds().isEmpty()) {
                        logVerbose("checkConnectionState launchNoPlayers " + ci);
                        launchNoPlayersActivity();
                    } else {
                        logVerbose("checkConnectionState launchMain " + ci);
                        launchMainActivity();
                    }
                    // don't animate this transition
                    overridePendingTransition(0, 0);
                } else if (mContext.isConnecting()) {
                    logVerbose("checkConnectionState disconnected " + ci);
                    startActivity(new Intent(this, ConnectingActivity.class));
                } else {
                    logVerbose("checkConnectionState disconnected " + ci);
                    startActivity(new Intent(this, ConnectActivity.class));
                }
                // we're headed to a new activity, dismiss the connecting dialog if it exists
                dismissConnectingDialog();
                finish();
            }
        }
        return ok;
    }

    protected void logVerbose(String msg, Object... args) {
        if (OSLog.isLoggable(OSLog.VERBOSE)) {
            String formatted = String.format(msg, args);
            OSLog.v(getClass().getSimpleName() + ": " + formatted);
        }
    }

    protected void launchNoPlayersActivity() {
        OSAssert.assertMainThread();
        Intent intent = new Intent(this, NoPlayersActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    protected void launchMainActivity() {
        OSAssert.assertMainThread();
        startActivity(MainActivity.Companion.newIntent(this));
    }

    protected boolean allowToastDisplay() {
        return false;
    }

    final private Object mEventReceiver = new Object() {
        @Subscribe
        public void whenAppPreferenceChanges(AppPreferenceChangeEvent event) {
            if (event.getKey().equals(SBPreferences.get().getKeepScreenOnKey())) {
                setWindowFlags();
            } else if (event.shouldTriggerRestart()) {
                mNeedRestartActivity = true;
            }
        }
    };

    final private Object mActivityLifecycleEventReceiver = new Object() {
        @Subscribe
        public void whenConnectivityChangeEvent(ConnectivityChangeEvent event) {
            logVerbose("whenConnectivityChangeEvent %s", event);
            checkConnectionState();
        }

        @Subscribe
        public void whenPlayerListChanges(PlayerListChangedEvent event) {
            if (event.getPlayerStatusList().isEmpty()) {
                launchNoPlayersActivity();
                finish();

                // don't animate this
                overridePendingTransition(0, 0);
            }
        }

        @Subscribe
        public void whenCurrentConnectionStateChanges(ConnectionStateChangedEvent event) {
            logVerbose("whenCurrentConnectionStateChanges %s", event);
            checkConnectionState();
        }

        @Subscribe
        public void whenActivePlayerChanges(ActivePlayerChangedEvent event) {
            PlayerStatus status = event.getPlayerStatus().orNull();
            if (status != null) {
                showPlayerToast(status);
            }
        }

        @Subscribe
        public void whenPendingConnectionChanges(PendingConnectionState event) {
            mPendingConnection = event.getPendingConnection();
            checkConnectionState();
        }
    };
}
