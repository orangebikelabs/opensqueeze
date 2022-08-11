/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.startup

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.StringRes
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import arrow.core.Either
import com.afollestad.materialdialogs.MaterialDialog
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.SBFragment
import com.orangebikelabs.orangesqueeze.common.SBContextProvider
import com.orangebikelabs.orangesqueeze.common.SBPreferences
import com.orangebikelabs.orangesqueeze.common.ServerType
import com.orangebikelabs.orangesqueeze.databinding.ConnectBinding
import com.orangebikelabs.orangesqueeze.databinding.ConnectserverItemBinding
import com.orangebikelabs.orangesqueeze.database.Server
import com.orangebikelabs.orangesqueeze.net.SendDiscoveryPacketService
import com.orangebikelabs.orangesqueeze.ui.AddNewServerDialog
import com.orangebikelabs.orangesqueeze.ui.LoginDialogFragment
import com.orangebikelabs.orangesqueeze.ui.WakeOnLanDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Fragment has server list and connection/discovery logic.
 *
 * @author tbsandee@orangebikelabs.com
 */
class ConnectFragment : SBFragment() {
    private var _binding: ConnectBinding? = null
    private val binding
        get() = _binding!!

    private var sendDiscoveryPacket: SendDiscoveryPacketService? = null

    private lateinit var adapter: ConnectAdapter

    private val viewModel: ConnectViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = ConnectAdapter()

        viewModel.events.observe(this) { event ->
            event.getContentIfNotHandled()?.let {
                return@let when (it) {
                    is ConnectViewModel.Events.ConnectionFailed -> {
                        showAlertDialog(R.string.connection_error_title, it.reason)
                    }
                    is ConnectViewModel.Events.ConnectionNeedsLogin -> {
                        try {
                            val ci = it.connectionInfo
                            val existing = parentFragmentManager.findFragmentByTag("login") as LoginDialogFragment?
                            existing?.dismiss()
                            val ldf = LoginDialogFragment.newInstance(this@ConnectFragment, ci.serverId, ci.serverName)
                            ldf.show(parentFragmentManager, "login")
                        } catch (e: IllegalStateException) {
                            // ignore if the fragment is being removed
                        }
                    }
                    is ConnectViewModel.Events.ServerAdded -> TODO()
                }
            }
        }

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ConnectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.addButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                when(val result = AddNewServerDialog.create(this@ConnectFragment, viewModel).show()) {
                    is Either.Left -> {
                        // nah, do nothing
                    }
                    is Either.Right -> {
                        mSbContext.startPendingConnection(result.value, "dumb")
                    }
                }

            }
        }
        binding.squeezenetworkButton.setOnClickListener { viewModel.createNewSqueezenetwork() }
        binding.discoveryToggle.isChecked = SBPreferences.get().isAutoDiscoverEnabled
        binding.discoveryToggle.setOnClickListener {
            SBPreferences.get().setAutoDiscover(binding.discoveryToggle.isChecked)
            setDiscoveryState()
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        setDiscoveryState()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.servers.collectLatest {
                    binding.list.isVisible = it.isNotEmpty()
                    binding.empty.isVisible = it.isEmpty()

                    adapter.submitList(it)
                }
            }
        }
    }

    private fun onServerItemClick(view: View, server: Server) {
        val ci = mSbContext.connectionInfo

        // if it's the same id just end the activity
        if (server._id == ci.serverId && mSbContext.isConnected) {
            requireActivity().finish()
        } else {
            mSbContext.startPendingConnection(server._id, server.servername)
        }
    }

    // this method is called by login dialog fragment when the results are found
    fun onLoginDialogResult(success: Boolean, serverId: Long, serverName: String) {
        if (success) {
            mSbContext.startPendingConnection(serverId, serverName)
        } else {
            showAlertDialog(R.string.connection_error_title, getString(R.string.error_invalid_credentials))
        }
    }


    private data class ServerItemContext(val serverOperations: ConnectViewModel.ServerOperation, val text: String) {
        override fun toString(): String {
            return text
        }
    }

    private class ServerItemAdapter(context: Context) :
            ArrayAdapter<ServerItemContext>(context, android.R.layout.simple_list_item_1) {
        override fun getItemId(position: Int): Long {
            return checkNotNull(getItem(position)).serverOperations.ordinal.toLong()
        }
    }

    private fun showContextMenu(view: View, server: Server) {
        val popup = ListPopupWindow(requireContext())
        popup.anchorView = view

        val adapter = ServerItemAdapter(requireContext())
        adapter.addAll(
                viewModel
                        .getAvailableServerOperations(server)
                        .map { ServerItemContext(it, getString(it.resId)) }
        )
        popup.setAdapter(adapter)
        popup.setContentWidth(resources.getDimensionPixelSize(R.dimen.serverlist_popup_list_width))
        popup.setOnItemClickListener { _, _, _, id ->
            popup.dismiss()

            when (val operation = ConnectViewModel.ServerOperation.values()[id.toInt()]) {
                ConnectViewModel.ServerOperation.WAKEONLANSETTINGS ->
                    WakeOnLanDialog.newInstance(this, server._id, server.servername)
                            .show()
                else -> {
                    viewModel.performServerOperation(operation, server)
                }
            }
        }
        popup.show()
    }

    override fun onPause() {
        super.onPause()
        sendDiscoveryPacket?.stopAsync()
    }

    override fun onResume() {
        super.onResume()
        sendDiscoveryPacket = SendDiscoveryPacketService(15, TimeUnit.SECONDS)
        sendDiscoveryPacket?.startAsync()
    }

    private fun setDiscoveryState() {
        binding.discoveryProgress.visibility = if (SBPreferences.get().isAutoDiscoverEnabled) View.VISIBLE else View.INVISIBLE
    }

    private fun showAlertDialog(@StringRes titleRid: Int, message: String) {
        MaterialDialog(requireContext())
                .icon(res = android.R.drawable.ic_dialog_alert)
                .title(res = titleRid)
                .message(text = message)
                .show()
    }

    private inner class ConnectAdapter : ListAdapter<Server, ConnectAdapter.ViewHolder>(DiffUtilCallback) {
        inner class ViewHolder(val binding: ConnectserverItemBinding) : RecyclerView.ViewHolder(binding.root) {
            init {
                binding.actionButton.setOnClickListener {
                    val item = getItem(bindingAdapterPosition)
                    showContextMenu(it, item)
                }
                binding.root.setOnClickListener {
                    val item = getItem(bindingAdapterPosition)
                    onServerItemClick(it, item)
                }
            }
        }


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = ConnectserverItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(layout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val server = getItem(position)
            holder.binding.name.text = server.servername
            val hasCredentials = server.serverkey != null
            val typeResource = when (server.servertype) {
                ServerType.DISCOVERED -> if (hasCredentials) R.drawable.ic_unlocked else R.drawable.ic_locked
                ServerType.PINNED -> R.drawable.ic_pin_outline
                ServerType.SQUEEZENETWORK -> R.drawable.ic_cloud
            }
            holder.binding.typeIcon.setImageResource(typeResource)
            holder.binding.connectedIcon.visibility = if (server._id == SBContextProvider.get().serverId) {
                if (SBContextProvider.get().isConnected || SBContextProvider.get().isConnecting) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            } else {
                View.GONE
            }
        }

    }

    object DiffUtilCallback : DiffUtil.ItemCallback<Server>() {
        override fun areItemsTheSame(oldItem: Server, newItem: Server): Boolean {
            return oldItem._id == newItem._id
        }

        override fun areContentsTheSame(oldItem: Server, newItem: Server): Boolean {
            return oldItem.servername == newItem.servername && oldItem.servertype == newItem.servertype
        }
    }
}