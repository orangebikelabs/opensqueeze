/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.browse

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.DrawerActivity
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor
import com.orangebikelabs.orangesqueeze.browse.common.Item
import com.orangebikelabs.orangesqueeze.browse.common.ItemType
import com.orangebikelabs.orangesqueeze.browse.common.SeparatorItem
import com.orangebikelabs.orangesqueeze.browse.node.BrowseNodeFragment
import com.orangebikelabs.orangesqueeze.browse.node.NodeItem
import com.orangebikelabs.orangesqueeze.browse.node.NodeItemAdapter
import com.orangebikelabs.orangesqueeze.common.NavigationItem
import com.orangebikelabs.orangesqueeze.common.NavigationItem.Companion.newFixedItem
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
class BrowseDrawerFragment : BrowseNodeFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // override this -- we don't want the option menu for this fragment to be impacting things
        setHasOptionsMenu(false)
    }

    override fun onLoaderDataReceived(data: List<Item?>?, empty: Boolean, complete: Boolean) {
        super.onLoaderDataReceived(data, empty, complete)

        // add a few extra items to the list
        adapter.apply {
            setNotifyOnChange(false)
            add(SeparatorItem())
            add(newDrawerNavigationItem(NavigationItem.Type.PLAYERS))
            add(newDrawerNavigationItem(NavigationItem.Type.DOWNLOADS))
            add(TextItem(requireContext().getString(R.string.navigation_search), R.drawable.ic_search))
            add(SeparatorItem())
            add(newDrawerNavigationItem(NavigationItem.Type.CUSTOMIZE_MENU))
            notifyDataSetChanged()
        }
    }

    override fun onItemClick(item: Item, itemView: View, position: Int) {
        val activity = requireActivity() as DrawerActivity
        activity.closeDrawers()
        if (item is DrawerNavigationItem) {
            val ni = newFixedItem(requireContext(), item.rootType)
            navigationManager.navigateTo(ni)
        } else if (item is TextItem) {
            if (item.iconRid == R.drawable.ic_search) {
                activity.expandSearchActionView()
            }
        } else {
            super.onItemClick(item, itemView, position)
        }
    }

    override val defaultBrowseStyle: BrowseStyle
        get() = BrowseStyle.BROWSE_DRAWER

    override fun getParentNodeId(): String {
        return NodeItem.HOME_NODE
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.browsedrawer_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkNotNull(browseListView)
    }

    override fun createListAdapter(): NodeItemAdapter {
        return DrawerItemAdapter(requireContext(), thumbnailProcessor)
    }

    override fun onStandardItemClick(item: StandardMenuItem, view: View, position: Int) {
        item as NodeItem
        val navItem = requireNotNull(item.getNavigationItem()) {
            "must have a navigation destination"
        }
        navigationManager.navigateTo(navItem)
    }

    private fun newDrawerNavigationItem(rootType: NavigationItem.Type): DrawerNavigationItem {
        return DrawerNavigationItem(requireContext().getString(rootType.titleRid), rootType.iconRid, rootType)
    }

    /** this adapter just overrides thumbtext items to be draweritems */
    internal class DrawerItemAdapter(context: Context, processor: ThumbnailProcessor) : NodeItemAdapter(context, processor) {
        override fun getItemType(item: Item): ItemType {
            return when (val type = super.getItemType(item)) {
                ItemType.IVT_THUMBTEXT -> ItemType.IVT_DRAWERITEM
                else -> type
            }
        }
    }

    internal class TextItem(private val text: String, @DrawableRes private val iconRid: Int) : Item() {

        override fun isEnabled(): Boolean {
            return true
        }

        override fun getText(): String {
            return this.text
        }

        @DrawableRes
        override fun getIconRid(): Int {
            return this.iconRid
        }

        override fun getBaseType(): ItemType {
            return ItemType.IVT_DRAWERITEM
        }

    }

    internal class DrawerNavigationItem(private val text: String, @DrawableRes private val iconRid: Int, val rootType: NavigationItem.Type) : Item() {
        override fun isEnabled(): Boolean {
            return true
        }

        override fun getText(): String {
            return this.text
        }

        @DrawableRes
        override fun getIconRid(): Int {
            return this.iconRid
        }

        override fun getBaseType(): ItemType {
            return ItemType.IVT_DRAWERITEM
        }
    }
}