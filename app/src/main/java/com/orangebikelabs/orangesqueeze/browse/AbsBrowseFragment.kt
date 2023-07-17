/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.LayoutRes
import androidx.loader.app.LoaderManager.LoaderCallbacks
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.loader.app.LoaderManager

import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.artwork.ThumbnailProcessor
import com.orangebikelabs.orangesqueeze.browse.common.BrowseRequest
import com.orangebikelabs.orangesqueeze.browse.common.CommandTools
import com.orangebikelabs.orangesqueeze.browse.common.Item
import com.orangebikelabs.orangesqueeze.common.*
import com.orangebikelabs.orangesqueeze.common.event.ItemActionButtonClickEvent
import com.orangebikelabs.orangesqueeze.common.event.ItemSliderChangedEvent
import com.orangebikelabs.orangesqueeze.common.event.TriggerListPreload
import com.orangebikelabs.orangesqueeze.compat.getParcelableCompat
import com.orangebikelabs.orangesqueeze.menu.AbsMenuFragment
import com.orangebikelabs.orangesqueeze.menu.ActionNames
import com.orangebikelabs.orangesqueeze.menu.MenuElement
import com.orangebikelabs.orangesqueeze.menu.MenuHelpers
import com.orangebikelabs.orangesqueeze.menu.NextWindowNames
import com.orangebikelabs.orangesqueeze.menu.SimpleImageItem
import com.orangebikelabs.orangesqueeze.menu.StandardMenuItem
import com.orangebikelabs.orangesqueeze.widget.HeaderCapable
import com.squareup.otto.Subscribe
import kotlinx.coroutines.launch

/**
 * Base fragment for use with the various browse lists/grids.
 *
 * @author tsandee
 */
abstract class AbsBrowseFragment<T : BrowseItemBaseAdapter, L> : AbsMenuFragment() {

    companion object {
        private val sArtworkLoadsAfterComplete = Runtime.getRuntime().availableProcessors() <= 1

        protected const val BROWSE_LOADER_ID = 0

        private const val SAVESTATE_MUTABLE_ARGS = "MutableArgs"
        private const val SAVESTATE_REFRESHORIGIN = "RefreshOrigin"
        private const val SAVESTATE_NEEDREFRESH = "NeedsRefresh"

        const val PARAM_SHORT_MODE = "browseShortMode"
        const val PARAM_BROWSE_STYLE = "browseStyle"
        const val PARAM_ALWAYS_REFRESH_ON_RESTART = "alwaysRefreshOnRestart"
    }

    /**
     * holds the effective browse style
     */
    protected lateinit var browseStyle: BrowseStyle

    /**
     * holds the browse style as detected from non-mutable fragment arguments
     */
    private var suppliedBrowseStyle: BrowseStyle? = null

    /**
     * should we always refresh on restart
     */
    private var alwaysRefreshOnRestart = false

    private var _mutableArguments: Bundle? = null

    private val navigationItem: NavigationItem?
        get() = NavigationItem.getNavigationItem(mutableArguments)

    private var browseView: ViewGroup? = null

    @LayoutRes
    protected var initBrowseViewLayoutRid = 0

    protected var browseListView: AbsListView? = null

    protected lateinit var thumbnailProcessor: ThumbnailProcessor

    lateinit var adapter: OSBrowseAdapter
        private set

    private var isShortMode = false

    /**
     * when we finish to the parent, do we force a refresh?
     */
    private var refreshOrigin = false

    /**
     * when we next start, should we trigger a refresh
     */
    private var needsRefresh = false

    private val browseHeaderCallbacks = BrowseHeaderCallbacks()
    private var browseHeader: View? = null


    // default to LIST
    open val defaultBrowseStyle: BrowseStyle
        get() {
            if (SBPreferences.get().browseGridCount > 0) {
                navigationItem?.let { ni ->
                    if (ni.type === NavigationItem.Type.BROWSENODE) {
                        return BrowseStyle.GRID
                    }
                    val commands = checkNotNull(ni.requestCommandSet?.commands) { "commands should always be supplied" }
                    val params = checkNotNull(ni.requestCommandSet?.parameters) { "parameters should always be supplied" }

                    if (CommandTools.commandsContain(commands, "browselibrary", "items")) {
                        if (!CommandTools.commandsContain(params, "mode:tracks") && !CommandTools.commandsContain(params, "mode:bmf")) {
                            return BrowseStyle.GRID
                        }
                    }
                    if (CommandTools.commandsContain(commands, "custombrowse", "browsejive")) {
                        var albumFilterFound = false
                        for (p in params) {
                            if (p.startsWith("album:")) {
                                albumFilterFound = true
                                break
                            }
                        }
                        if (!albumFilterFound) {
                            return BrowseStyle.GRID
                        }
                    }
                    if (CommandTools.commandsContain(commands, "myapps", "items")) {
                        return BrowseStyle.GRID
                    }
                    if (CommandTools.commandsContain(commands, "imagebrowser", "items")) {
                        return BrowseStyle.GRID
                    }
                    if (params.contains("slideshow:1") || params.contains("type:slideshow")) {
                        return BrowseStyle.GRID
                    }
                    if (CommandTools.commandsContain(commands, "artists")) {
                        if (CommandTools.commandsContain(params, "menu:album")) {
                            return BrowseStyle.GRID
                        }
                    }
                    if (CommandTools.commandsContain(commands, "albums")) {
                        return BrowseStyle.GRID
                    }
                }
            }
            return BrowseStyle.LIST
        }

    private inner class EventReceiver {
        @Subscribe
        fun whenItemSliderChanged(event: ItemSliderChangedEvent) {
            if (!event.appliesTo(this@AbsBrowseFragment)) {
                return
            }

            val item = event.item
            val menuElement = item.menuElement

            executeAction(item, menuElement, ActionNames.GO, null, event.newValue.toString())
        }

        @Subscribe
        fun whenItemActionButtonClicked(event: ItemActionButtonClickEvent) {
            if (!event.appliesTo(this@AbsBrowseFragment)) {
                return
            }

            this@AbsBrowseFragment.onActionButtonClicked(event.item, event.actionButtonView, event.position)
        }
    }

    private val eventReceiver = EventReceiver()

    override fun getMutableArguments(): Bundle {
        return checkNotNull(_mutableArguments)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initBrowseViewLayoutRid = 0

        if (savedInstanceState != null) {
            _mutableArguments = checkNotNull(savedInstanceState.getBundle(SAVESTATE_MUTABLE_ARGS))
            refreshOrigin = savedInstanceState.getBoolean(SAVESTATE_REFRESHORIGIN, false)
            needsRefresh = savedInstanceState.getBoolean(SAVESTATE_NEEDREFRESH, false)
        } else {
            // create copy of arguments that can be modified and persist that in activity state
            val newArgumentsBundle = Bundle()
            arguments?.let {
                newArgumentsBundle.putAll(it)
            }
            _mutableArguments = newArgumentsBundle
        }

        alwaysRefreshOnRestart = mutableArguments.getBoolean(PARAM_ALWAYS_REFRESH_ON_RESTART, false)

        suppliedBrowseStyle = mutableArguments.getParcelableCompat(PARAM_BROWSE_STYLE, BrowseStyle::class.java)
        browseStyle = suppliedBrowseStyle ?: defaultBrowseStyle

        isShortMode = mutableArguments.getBoolean(PARAM_SHORT_MODE, false)

        thumbnailProcessor = ThumbnailProcessor(requireContext())

        // because we have subclasses, use standalone eventreceiver
        mBus.register(eventReceiver)
    }

    override fun getSnackbarView(): View? {
        return browseView
    }

    @Suppress("DEPRECATION")
    @Deprecated("by superclass")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        LoaderManager.getInstance(this).initLoader(BROWSE_LOADER_ID, mutableArguments, createLoaderCallbacks())
    }

    protected open fun onLoaderDataReceived(data: L?, empty: Boolean, complete: Boolean) {
        if (sArtworkLoadsAfterComplete) {
            // pause/unpause the thumbnail loader depending on whether or not the load is complete
            thumbnailProcessor.setPaused(!complete)
        }
        if (complete) {
            // kick off a preload now that we've completed loading
            BusProvider.getInstance().postFromMain(TriggerListPreload())
        }

        if (!empty) {
            setVisibleBrowseView(R.id.browseview)
        } else {
            if (complete) {
                setVisibleBrowseView(android.R.id.empty)
            } else {
                setVisibleBrowseView(R.id.loading)
            }
        }
        if (prepareBrowseHeader(browseStyle)) {
            setupBrowseHeader()
        }
        // TODO show error message if it exists
    }

    /**
     * subclasses must override this to change the loader
     */
    protected abstract fun createLoaderCallbacks(): LoaderCallbacks<L>

    override fun onDestroy() {
        super.onDestroy()

        mBus.unregister(eventReceiver)
    }


    override fun onStart() {
        super.onStart()

        thumbnailProcessor.onStart()
    }

    override fun onResume() {
        super.onResume()

        browseHeaderCallbacks.addButton?.visibility = View.VISIBLE
        browseHeaderCallbacks.playButton?.visibility = View.VISIBLE
    }

    override fun onStop() {
        super.onStop()

        thumbnailProcessor.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val writeArguments = mutableArguments

        outState.putBundle(SAVESTATE_MUTABLE_ARGS, writeArguments)
        outState.putBoolean(SAVESTATE_REFRESHORIGIN, refreshOrigin)
        outState.putBoolean(SAVESTATE_NEEDREFRESH, needsRefresh)
    }

    override fun populatePersistentExtras(intent: Intent) {
        super.populatePersistentExtras(intent)

        // propagate browse style
        if (suppliedBrowseStyle != null) {
            intent.putExtra(PARAM_BROWSE_STYLE, suppliedBrowseStyle as Parcelable?)
        }

        intent.putExtra(PARAM_ALWAYS_REFRESH_ON_RESTART, alwaysRefreshOnRestart)
    }

    override fun requery(args: Bundle?) {
        if (args != null) {
            mutableArguments.putAll(args)
        }

        if (!isResumed) {
            needsRefresh = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.browse_fragment, container, false)
    }

    private fun initializeBrowseView(view: View, style: BrowseStyle) {
        if (initBrowseViewLayoutRid == style.layoutId) {
            return
        }

        initBrowseViewLayoutRid = style.layoutId

        val container = view.findViewById<FrameLayout>(R.id.browseview_container)
        val removeView = container.findViewById<View>(R.id.browseview)
        container.removeView(removeView)

        // force reset of header
        browseHeader = null
        browseView = requireActivity().layoutInflater.inflate(initBrowseViewLayoutRid, null, false) as ViewGroup

        browseView?.id = R.id.browseview

        container.addView(browseView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        browseListView = if (browseView is AbsListView) {
            browseView as AbsListView
        } else {
            null
        }
        browseListView?.also { blv ->
            if (blv is GridView) {
                blv.numColumns = GridView.AUTO_FIT
                blv.columnWidth = SBPreferences.get().gridCellWidth
                blv.stretchMode = GridView.STRETCH_SPACING_UNIFORM
                blv.horizontalSpacing = 0
                blv.verticalSpacing = SBPreferences.get().gridCellSpacing
            }

            ScrollingState.monitorScrolling(blv)

            blv.setOnItemClickListener { parent, itemView, pos, _ ->
                val item = parent.getItemAtPosition(pos) as? Item
                if (item != null) {
                    this@AbsBrowseFragment.onItemClick(item, itemView, pos)
                }
            }
            blv.setOnItemLongClickListener { parent, itemView, pos, _ ->
                var consumed = false
                val item = parent.getItemAtPosition(pos) as? Item
                if (item is StandardMenuItem) {
                    var actionButtonView: View? = itemView.findViewById(R.id.action_button)
                    if (actionButtonView == null) {
                        actionButtonView = checkNotNull(itemView)
                    }
                    consumed = onActionButtonClicked(item, actionButtonView, pos)
                }
                consumed
            }
            createListAdapter().apply {
                adapter = this
                blv.adapter = this
            }

            if (isShortMode) {
                blv.isFastScrollEnabled = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        browseListView = null
        browseView = null
        browseHeader = null

        initBrowseViewLayoutRid = 0

        browseHeaderCallbacks.clear()
    }

    override fun getFillPlayerId(): PlayerId? {
        return navigationItem?.playerId
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeBrowseView(view, browseStyle)

        if (isShortMode) {
            // in short mode we don't display the progress bar
            view.findViewById<View>(R.id.listload_progress)?.visibility = View.GONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (needsRefresh) {
                    // issue a refresh very soon
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (isAdded) {
                            requery(null)
                        }
                    }
                    needsRefresh = false
                }

                if (alwaysRefreshOnRestart) {
                    // next time, refresh
                    needsRefresh = true
                }
            }
        }
    }

    protected fun setVisibleBrowseView(viewId: Int) {
        val container = view ?: return

        val frame = container.findViewById<FrameLayout>(R.id.browseview_container)
        if (frame != null) {
            frame.findViewById<View>(viewId)?.visibility = View.VISIBLE
            for (i in 0 until frame.childCount) {
                val child = frame.getChildAt(i)

                if (child != null && child.id != viewId && child !is ViewStub) {
                    child.visibility = View.INVISIBLE
                }
            }
        } else {
            container.findViewById<View>(viewId)?.visibility = View.VISIBLE
        }
    }

    //	@Override
    //	public void choiceDialogOnClick(Object choice, int index, Bundle extra) {
    //		int dialogId = extra.getInt(EXTRA_DIALOG_ID);
    //		if (dialogId == DIALOG_ID_COMPILATION) {
    //			getContext().getPreferences().setBrowseCompilationHidden(index != 0);
    //			requery(null);
    //		} else if (dialogId == DIALOG_ID_SORTORDER) {
    //			SortOrderOptions options = (SortOrderOptions) choice;
    //			BaseBrowseAdapter adapter = (BaseBrowseAdapter) mListView.getAdapter();
    //			adapter.storeSortOrderPreference(options.mSortBy);
    //			requery(null);
    //		}
    //	}

    protected open fun onItemClick(item: Item, itemView: View, position: Int) {
        if (item is StandardMenuItem) {
            onStandardItemClick(item, itemView, position)
        }
    }

    protected open fun onStandardItemClick(item: StandardMenuItem, view: View, position: Int) {
        val menuElement = item.menuElement
        val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
        if (menuElement.isCheckbox && checkbox != null) {
            handleCheckbox(checkbox, item, menuElement)
        } else if (menuElement.isRadio) {
            handleRadio(item, menuElement)
        } else if (item is SimpleImageItem) {
            val image = item.imageUrl.getOrNull()
            if (image != null) {
                executeShowBigArtwork(item.itemTitle, image)
            }
        } else {
            executeDefaultClickAction(view, item, menuElement)
        }
    }

    protected abstract fun createListAdapter(): T

    protected open fun newRequest(args: Bundle): BrowseRequest {
        val request = BrowseRequest(effectivePlayerId)
        val item = checkNotNull(navigationItem)

        val commands = checkNotNull(item.requestCommandSet?.commands) { "commands is required" }
        request.commands = commands

        val params = checkNotNull(item.requestCommandSet?.parameters) { "params is required" }
        request.setParameters(params)

        request.isCacheable = true
        return request
    }

    protected open fun onActionButtonClicked(item: StandardMenuItem, actionButtonView: View, position: Int): Boolean {
        return item.showContextMenu(this, actionButtonView)
    }

    private fun executeDefaultClickAction(itemView: View, item: StandardMenuItem, element: MenuElement) {
        var handled = false
        if (element.isPlayItem) {
            val playMode = SBPreferences.get().trackSelectPlayMode
            val action = playMode.action

            handled = when (playMode) {
                PlayOptions.PROMPT -> item.showContextMenu(this, itemView)
                PlayOptions.ADD, PlayOptions.INSERT -> {
                    checkNotNull(action) { "action can't be null for these playmodes" }
                    executeAction(item, element, action, NextWindowNames.NOREFRESH, null)
                }
                PlayOptions.PLAY -> {
                    checkNotNull(action) { "action can't be null for these playmodes" }
                    executeAction(item, element, action, NextWindowNames.NOWPLAYING, null)
                }
            }
        }
        if (!handled) {
            executeAction(item, element, ActionNames.GO)
            if (element.isChoice) {
                // this call might change the displayed value
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun handleRadio(checkedItem: StandardMenuItem, menuItem: MenuElement) {
        // execute the action first
        executeAction(checkedItem, menuItem, ActionNames.GO)

        val count = adapter.count
        for (i in 0 until count) {
            val item = adapter.getItem(i) as? StandardMenuItem ?: continue
            val elem = item.menuElement
            if (elem.isRadio) {
                item.menuElement.radio = if (item === checkedItem) 1 else 0
            }
        }
        adapter.notifyDataSetChanged()
    }

    private fun handleCheckbox(checkbox: CheckBox, item: StandardMenuItem, menuItem: MenuElement) {
        val newValue = !checkbox.isChecked
        item.mutatedCheckboxChecked = newValue
        executeAction(item, menuItem, if (newValue) "on" else "off")
        adapter.notifyDataSetChanged()
    }

    @JvmOverloads
    protected fun executeAction(jsonItem: StandardMenuItem, elem: MenuElement, actionName: String, nextWindow: String? = null, fillValue: String? = null): Boolean {
        val action = MenuHelpers.getAction(elem, actionName)
        val title = jsonItem.text1
        execute(title, elem, action, nextWindow, false, fillValue)
        return action != null
    }

    private fun prepareBrowseHeader(style: BrowseStyle): Boolean {
        if (style != BrowseStyle.LIST && style != BrowseStyle.GRID) {
            return false
        }

        if (adapter.isEmpty) {
            return false
        }

        return navigationItem?.playCommandSet != null || navigationItem?.addCommandSet != null
    }

    private fun setupBrowseHeader() {
        val blv = checkNotNull(browseListView)
        val hc = blv as HeaderCapable

        if (browseHeader == null) {
            val saveAdapter = blv.adapter
            blv.adapter = null

            browseHeader = LayoutInflater.from(context).inflate(R.layout.browseheader_controls, blv, false).apply {
                hc.addHeaderView(this, null, false)
                browseHeaderCallbacks.bind(this)
            }

            blv.adapter = saveAdapter
        }

        val ni = checkNotNull(navigationItem)
        browseHeaderCallbacks.playButton?.visibility = if (ni.playCommandSet != null) View.VISIBLE else View.GONE
        browseHeaderCallbacks.addButton?.visibility = if (ni.addCommandSet != null) View.VISIBLE else View.GONE

    }

    internal inner class BrowseHeaderCallbacks {

        var playButton: ImageButton? = null
        var addButton: ImageButton? = null

        fun bind(view: View) {
            val ni = checkNotNull(navigationItem)
            playButton = view.findViewById(R.id.header_play_button)
            playButton?.setOnClickListener { executeCommandSet(ni, checkNotNull(ni.playCommandSet), NextWindowNames.NOWPLAYING) }
            playButton?.setOnLongClickListener {
                if (ni.playNextCommandSet != null) {
                    executeCommandSet(ni, checkNotNull(ni.playNextCommandSet), NextWindowNames.NOWPLAYING)
                    true
                } else {
                    false
                }
            }
            addButton = view.findViewById(R.id.header_add_button)
            addButton?.setOnClickListener { executeCommandSet(ni, checkNotNull(ni.addCommandSet), null) }
        }

        fun clear() {
            playButton = null
            addButton = null
        }
    }
}
