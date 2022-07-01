/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.download;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;

import android.view.MenuItem;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.SBActivity;
import com.orangebikelabs.orangesqueeze.common.NavigationCommandSet;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;
import com.orangebikelabs.orangesqueeze.common.OSAssert;
import com.orangebikelabs.orangesqueeze.ui.MainActivity;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

/**
 * @author tsandee
 */
@RuntimePermissions
public class PrepareDownloadActivity extends SBActivity {

    public static Intent newIntent(Context context, List<String> commands, List<String> parameters, String downloadTitle) {
        Intent intent = new Intent(context, PrepareDownloadActivity.class);
        NavigationCommandSet ncs = new NavigationCommandSet(commands, parameters);
        NavigationItem item = NavigationItem.Companion.newPrepareDownloadItem(downloadTitle, ncs);
        NavigationItem.Companion.putNavigationItem(intent, item);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.toolbar_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        NavigationItem item = NavigationItem.Companion.getNavigationItem(getIntent());
        OSAssert.assertNotNull(item, "cannot be null");

        String title = item.getName();
        getSupportActionBar().setTitle(getString(R.string.download_activity_title, title));

        if (savedInstanceState == null) {
            Intent intent = getIntent();
            OSAssert.assertNotNull(intent, "intent can't be null");

            Bundle extras = intent.getExtras();
            OSAssert.assertNotNull(extras, "extras can't be null");

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.toolbar_content, PrepareDownloadFragment.newInstance(item));
            transaction.commitNow();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // delay
        AndroidSchedulers.mainThread().scheduleDirect(() -> {
            if (mStarted) {
                PrepareDownloadActivityPermissionsDispatcher.requireExternalStorageWithPermissionCheck(PrepareDownloadActivity.this);
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        PrepareDownloadActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent upIntent = new Intent(this, MainActivity.class);
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is not part of the application's task, so create a new task
                // with a synthesized back stack.
                TaskStackBuilder.create(this).addNextIntent(upIntent).startActivities();
                finish();
            } else {
                // just finish this activity, the parent should be above it
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void requireExternalStorage() {
        // do nothing
    }

    @OnShowRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void onShowRationale(final PermissionRequest request) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.permission_write_external_storage_rationale_title)
                .setMessage(R.string.permission_write_external_storage_rationale_content)
                .setPositiveButton(R.string.ok, (dlg, which) -> request.proceed())
                .show();
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void showDeniedExternalStorage() {
        Snackbar.make(getContentView(), R.string.permission_write_external_storage_denied, Snackbar.LENGTH_LONG)
                .show();
    }

    @OnNeverAskAgain(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void showNeverAskExternalStorage() {
        Snackbar.make(getContentView(), R.string.permission_write_external_storage_neverask, Snackbar.LENGTH_LONG)
                .show();
    }
}
