/*
 * Copyright (c) 2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download

import android.Manifest
import android.os.Build.VERSION
import androidx.fragment.app.Fragment
import com.fondesa.kpermissions.PermissionStatus
import com.fondesa.kpermissions.extension.permissionsBuilder
import com.fondesa.kpermissions.extension.send
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.OSLog

abstract class StoragePermissionHelper {
    companion object {
        fun getInstance(fragment: Fragment): StoragePermissionHelper {
            return if(VERSION.SDK_INT <= 28) {
                Android9StoragePermissionHelper(fragment)
            } else {
                Android13StoragePermissionHelper()
            }
        }
    }


    abstract fun send(grantedCallback: () -> Unit)

    class Android13StoragePermissionHelper : StoragePermissionHelper() {
        override fun send(grantedCallback: () -> Unit) {
            grantedCallback()
        }
    }

    class Android9StoragePermissionHelper(private val fragment: Fragment) : StoragePermissionHelper() {

        private val storagePermissionRequest by lazy {
            fragment.permissionsBuilder(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE).build()
        }

        override fun send(grantedCallback: () -> Unit) {
            storagePermissionRequest.send {
                when (it[0]) {
                    is PermissionStatus.Granted -> grantedCallback()
                    is PermissionStatus.Denied.Permanently -> showNeverAskExternalStorage()
                    is PermissionStatus.Denied.ShouldShowRationale -> onShowRationale()
                    is PermissionStatus.RequestRequired -> {
                        OSLog.e("required required")
                    }
                }
            }
        }

        private fun onShowRationale() {
            val builder = MaterialAlertDialogBuilder(fragment.requireContext())
            builder.setTitle(R.string.permission_write_external_storage_rationale_title)
                    .setMessage(R.string.permission_write_external_storage_rationale_content)
                    .setPositiveButton(R.string.ok) { _, _: Int -> }
                    .show()
        }

        private fun showDeniedExternalStorage() {
            Snackbar.make(fragment.requireView(), R.string.permission_write_external_storage_denied, Snackbar.LENGTH_LONG)
                    .show()
        }

        private fun showNeverAskExternalStorage() {
            Snackbar.make(fragment.requireView(), R.string.permission_write_external_storage_neverask, Snackbar.LENGTH_LONG)
                    .show()
        }
    }

}