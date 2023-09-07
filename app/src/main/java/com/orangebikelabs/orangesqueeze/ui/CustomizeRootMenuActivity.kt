/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.commitNow

import com.google.common.collect.ComparisonChain
import com.nhaarman.listviewanimations.BaseAdapterDecorator
import com.nhaarman.listviewanimations.itemmanipulation.dragdrop.TouchViewDraggableManager
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.DrawerActivity
import com.orangebikelabs.orangesqueeze.app.SBFragment
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor
import com.orangebikelabs.orangesqueeze.browse.common.ItemType
import com.orangebikelabs.orangesqueeze.browse.node.NodeItem
import com.orangebikelabs.orangesqueeze.browse.node.NodeItemAdapter
import com.orangebikelabs.orangesqueeze.common.PlayerId
import com.orangebikelabs.orangesqueeze.databinding.CustomizerootmenusBinding
import com.orangebikelabs.orangesqueeze.menu.MenuElement
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem

import java.text.Collator

/**
 * Activity used to reorder/select root menu items.
 */
class CustomizeRootMenuActivity : DrawerActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialized in onCreate() above
        drawerBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GRAVITY_PLAYER_DRAWER)

        supportActionBar.setDisplayHomeAsUpEnabled(true)
        supportActionBar.setDisplayShowTitleEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.content_frame, CustomizeFragment(), null)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val upIntent = Intent(this, MainActivity::class.java)
            if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                // This activity is not part of the application's task, so create a new task
                // with a synthesized back stack.
                TaskStackBuilder.create(this).addNextIntent(upIntent).startActivities()
                finish()
            } else {
                // just finish this activity, the parent should be above it
                finish()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun getSupportActionBar(): ActionBar {
        return checkNotNull(super.getSupportActionBar())
    }

    class CustomizeFragment : SBFragment() {
        private lateinit var thumbnailProcessor: ThumbnailProcessor
        private lateinit var listAdapterDecorator: BaseAdapterDecorator
        private lateinit var nodeAdapter: CustomizeNodeAdapter

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            thumbnailProcessor = ThumbnailProcessor(requireContext())

            nodeAdapter = CustomizeNodeAdapter(requireContext(), thumbnailProcessor)
            listAdapterDecorator = object : BaseAdapterDecorator(nodeAdapter) {
            }
        }

        override fun onPause() {
            super.onPause()

            val nodes = mutableListOf<String>()
            for (i in 0 until listAdapterDecorator.count) {
                val item = listAdapterDecorator.getItem(i) as CustomizeNodeItem
                if (item.mutatedCheckboxChecked) {
                    nodes.add(item.id)
                }
            }
            mSbContext.rootBrowseNodes = nodes
        }

        override fun onStop() {
            super.onStop()

            thumbnailProcessor.onStop()
        }

        override fun onStart() {
            super.onStart()

            thumbnailProcessor.onStart()

            mSbContext.playerId?.let {
                refreshNodeList(it)
            }
        }

        private fun refreshNodeList(playerId: PlayerId) {
            val menus = mSbContext.getPlayerMenus(playerId)
            val menuCollection = menus.rootMenus
                    .filterNot {
                        it.id == null || it.node == null || it.isBlacklisted
                    }
                    .toMutableList()

            // sort the
            menuCollection.sortWith { v1, v2 ->
                ComparisonChain.start()
                        .compare(v1.weight, v2.weight)
                        .compare(v1.text, v2.text, Collator.getInstance())
                        .result()
            }

            val checkedItems = mutableSetOf<MenuElement>()
            val accessibleItems = mutableSetOf<MenuElement>()

            NodeItem.getRootMenu(playerId).toList().forEach {
                val elem = it.menuElement

                val id = checkNotNull(elem.id) { "id shouldn't be null, we removed the null ones" }

                val newItem = CustomizeNodeItem(id, elem)
                newItem.mutatedCheckboxChecked = true

                checkedItems.add(elem)
                nodeAdapter.add(newItem)
            }
            calculateAccessibleNodes(NodeItem.HOME_NODE, menuCollection, accessibleItems)

            // add some apps not in hierarchy, but available
            calculateAccessibleNodes("", menuCollection, accessibleItems)

            // remove the items we've already added
            accessibleItems.removeAll(checkedItems)

            accessibleItems.forEach {
                val id = checkNotNull(it.id) { "id shouldn't be null, we removed the null ones" }
                nodeAdapter.add(CustomizeNodeItem(id, it))
            }
        }

        private var _binding: CustomizerootmenusBinding? = null
        private val binding get() = _binding!!

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            _binding = CustomizerootmenusBinding.inflate(inflater, container, false)
            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            binding.list.setOnItemClickListener { _, _, position, _ ->
                val item = listAdapterDecorator.getItem(position) as CustomizeNodeItem
                item.mutatedCheckboxChecked = !item.mutatedCheckboxChecked
                listAdapterDecorator.notifyDataSetChanged()
            }
            binding.list.enableDragAndDrop()
            binding.list.setDraggableManager(TouchViewDraggableManager(R.id.icon))
            binding.list.setOnItemMovedListener { _, _ ->
                // do nothing
            }

            listAdapterDecorator.setAbsListView(binding.list)
            binding.list.adapter = listAdapterDecorator
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        // strip out all non-accessible nodes
        private fun calculateAccessibleNodes(parentNode: String?, allElements: MutableList<MenuElement>, accessibleElements: MutableSet<MenuElement>) {
            allElements
                    .filter { it.node == parentNode }
                    .forEach {
                        if (accessibleElements.add(it)) {
                            calculateAccessibleNodes(it.id, allElements, accessibleElements)
                        }
                    }
        }
    }

    class CustomizeNodeAdapter(context: Context, processor: ThumbnailProcessor) : NodeItemAdapter(context, processor) {

        /**
         * override to include a breadcrumb-style hierarchy in view of node name
         */
        override fun bindText1(item: StandardMenuItem, text1: TextView) {
            var title = item.itemTitle
            val parentId = item.menuElement.node
            val parentText = getNodeDisplayName(parentId)
            item as CustomizeNodeItem
            if (!item.mutatedCheckboxChecked && parentText != null) {
                title = "$parentText \u2192 $title"
            }
            text1.text = title
        }

        /**
         * lookup a node's display name by id
         */
        private fun getNodeDisplayName(nodeId: String?): String? {
            if (nodeId == null) return null

            for (i in 0 until count) {
                val item = getItem(i) as CustomizeNodeItem
                if (item.id == nodeId) {
                    return item.itemTitle
                }
            }
            return null
        }

        /**
         * override to set up the checkbox properly
         */
        override fun bindCheckbox(item: StandardMenuItem, checkbox: CheckBox) {
            val cItem = item as CustomizeNodeItem

            val mutatedState = cItem.mutatedCheckboxChecked
            checkbox.isChecked = mutatedState
            checkbox.isClickable = false
            checkbox.isFocusable = false
            checkbox.isFocusableInTouchMode = false
        }
    }

    class CustomizeNodeItem(id: String, elem: MenuElement) : NodeItem(id, elem, true) {
        init {
            mutatedCheckboxChecked = false
        }

        @Synchronized
        override fun getMutatedCheckboxChecked(): Boolean {
            return checkNotNull(super.getMutatedCheckboxChecked())
        }

        override fun calculateType(): ItemType {
            return ItemType.IVT_CUSTOMIZEROOT
        }
    }
}
