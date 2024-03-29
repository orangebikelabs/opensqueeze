/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.download

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.SBFragment
import com.orangebikelabs.orangesqueeze.common.MenuTools
import com.orangebikelabs.orangesqueeze.databinding.DownloadsListBinding
import com.orangebikelabs.orangesqueeze.ui.TrackDownloadPreferenceActivity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * @author tsandee
 */
class ViewDownloadsFragment : SBFragment() {
    private var _binding: DownloadsListBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var adapter: ViewDownloadsAdapter

    private val viewModel: ViewDownloadsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = ViewDownloadsAdapter(requireContext())
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.viewdownloads, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.menu_downloadpreferences -> {
                        startActivity(Intent(requireActivity(), TrackDownloadPreferenceActivity::class.java))
                        true
                    }
                    R.id.menu_downloadclearall -> {
                        viewModel.clearDownloads()

                        // stop any active downloads
                        val stopDownloads = DownloadService.getStopDownloadsIntent(requireActivity())
                        requireActivity().startService(stopDownloads)
                        true
                    }
                    else -> false
                }
            }

            override fun onPrepareMenu(menu: Menu) {
                MenuTools.setVisible(menu, R.id.menu_downloadclearall, !adapter.isEmpty)
                MenuTools.setVisible(menu, R.id.menu_startdownload, false)
            }
        }, this, Lifecycle.State.RESUMED)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DownloadsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @FlowPreview
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.downloadList.emptyView = binding.empty
        binding.empty.setText(R.string.loading_text)
        binding.downloadList.setAdapter(adapter)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloads.collectLatest { downloads ->
                    binding.empty.setText(R.string.empty_downloads_text)
                    adapter.startUpdate()
                    downloads.forEach {
                        adapter.updateDownloadElement(it.downloadbatch, it._id, it.downloadtitle, it.downloadstatus)
                    }
                    adapter.finalizeUpdate()
                    refreshOptionsMenu()
                }
            }
        }
    }

    private fun refreshOptionsMenu() {
        val activity: FragmentActivity? = activity
        if (activity != null && isAdded && !activity.isFinishing) {
            activity.invalidateMenu()
        }
    }
}