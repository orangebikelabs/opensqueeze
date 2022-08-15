/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.ui

import com.orangebikelabs.orangesqueeze.app.SBDialogFragment
import com.orangebikelabs.orangesqueeze.common.ServerType
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.orangebikelabs.orangesqueeze.R
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.orangebikelabs.orangesqueeze.compat.getParcelableCompat
import com.orangebikelabs.orangesqueeze.databinding.LoginBinding
import kotlinx.parcelize.Parcelize

/**
 * @author tbsandee@orangebikelabs.com
 */
class LoginFragment : SBDialogFragment() {

    @Parcelize
    data class Result(val loginSuccess: Boolean, val serverId: Long, val serverName: String) : Parcelable

    companion object {
        private const val ARG_RESULT_KEY = "LoginResultKey"
        private const val ARG_SERVER_ID = "ServerId"
        private const val ARG_SERVER_NAME = "ServerName"

        private const val RESULT = "LoginResult"

        fun newInstance(resultKey: String, serverId: Long, serverName: String): LoginFragment {
            val retval = LoginFragment()
            retval.arguments = bundleOf(ARG_RESULT_KEY to resultKey, ARG_SERVER_ID to serverId, ARG_SERVER_NAME to serverName)
            return retval
        }

        fun getResult(bundle: Bundle): Result {
            return checkNotNull(bundle.getParcelableCompat(RESULT, Result::class.java))
        }
    }

    private val viewModel: LoginViewModel by viewModels()

    private var serverId = 0L
    private lateinit var serverName: String
    private lateinit var resultKey: String
    private lateinit var serverType: ServerType
    private lateinit var serverHost: String
    private var serverPort = 0

    // should we block clicks from being processed, set to true during login check
    private var blockClicks = false

    // set up viewbinding
    private var _binding: LoginBinding? = null
    private val binding
        get() = _binding!!

    private var connectButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        serverId = requireArguments().getLong(ARG_SERVER_ID)
        serverName = checkNotNull(requireArguments().getString(ARG_SERVER_NAME))
        resultKey = checkNotNull(requireArguments().getString(ARG_RESULT_KEY))

        // observe when viewmodel reports login result
        viewModel.events.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                return@let when (it) {
                    is LoginViewModel.Events.Login -> {
                        setFragmentResult(resultKey,
                                bundleOf(RESULT to Result(it.success, serverId, serverName))
                        )
                        dismiss()
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.login_title, serverName))
        builder.setCancelable(true)

        val rootView = layoutInflater.inflate(R.layout.login, null, false)
        _binding = LoginBinding.bind(rootView)
        binding.forgotPassword.setOnClickListener { goForgotPassword() }
        binding.password.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    connectListener.onClick(v)
                }
                // absorb action_up as well
                return@setOnKeyListener true
            } else {
                return@setOnKeyListener false
            }
        }
        builder.setView(rootView)
        builder.setPositiveButton(R.string.connect_button, null)
        builder.setNegativeButton(R.string.cancel) { _, _ -> }
        return builder.create()
    }

    override fun onStart() {
        super.onStart()

        // the DialogFragment has .show() called in onStart(), and we need to override the listener at that time
        val dialog = requireDialog() as AlertDialog

        // override the connect button
        connectButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        connectButton?.setOnClickListener(connectListener)

        // access the database and populates the username/password fields properly in the background
        lifecycleScope.launchWhenStarted {
            val result = viewModel.loadUsernamePassword(serverId)
            if (result != null) {
                binding.username.setText(result.username)
                binding.password.setText(result.password)
            } else {
                throw IllegalStateException("missing server record")
            }
        }
    }

    private fun goForgotPassword() {
        val url: String = if (serverType === ServerType.SQUEEZENETWORK) {
            "https://mysqueezebox.com/user/login"
        } else {
            "http://$serverHost:$serverPort/settings/index.html"
        }
        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        startActivity(i)
    }

    /**
     * called when add button is clicked
     */
    private val connectListener = View.OnClickListener {
        if (blockClicks) {
            return@OnClickListener
        }
        blockClicks = true
        connectButton?.isEnabled = false
        val username = binding.username.getTextWithDefault()
        val password = binding.password.getTextWithDefault()
        viewModel.setLoginCredentials(username, password)
    }

    private fun TextView.getTextWithDefault(defaultValue: String = ""): String {
        val value = this.text ?: return defaultValue
        return value.toString()
    }
}