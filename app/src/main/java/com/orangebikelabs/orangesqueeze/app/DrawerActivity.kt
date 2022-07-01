/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.app

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener
import androidx.appcompat.app.ActionBarDrawerToggle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.ActionBar
import com.orangebikelabs.orangesqueeze.BuildConfig
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.NavigationItem
import com.orangebikelabs.orangesqueeze.common.NavigationManager
import com.orangebikelabs.orangesqueeze.common.OSLog
import com.orangebikelabs.orangesqueeze.databinding.DrawerBinding
import com.orangebikelabs.orangesqueeze.databinding.ToolbarBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import java.lang.IllegalStateException

/**
 * Activity that provides support for the standard browse and player drawers in Orange Squeeze.
 *
 * @author tsandee
 */
abstract class DrawerActivity : SBActivity() {

    companion object {
        const val GRAVITY_BROWSE_DRAWER = GravityCompat.START
        const val GRAVITY_PLAYER_DRAWER = GravityCompat.END

        /**
         * store the current nav item as state to be restored when the app loads
         */
        private const val STATE_NAV_ITEM = BuildConfig.APPLICATION_ID + ".state.navItem"

        /**
         * store the nav stack as state to be restored
         */
        private const val STATE_NAV_STACK = BuildConfig.APPLICATION_ID + ".state.navStack"
    }

    class NavigationAdapterItem(val navigationItem: NavigationItem) {
        override fun toString(): String {
            return navigationItem.name
        }
    }

    val currentItemLevel: Int
        get() = navigationStack.size + 1

    /**
     * the navigation manager
     */
    lateinit var navigationManager: NavigationManager
        private set

    /**
     * contains the current stack of navigation items, EXCLUDING the currently loaded one
     */
    var navigationStack = listOf<NavigationItem>()

    /**
     * the currently loaded nav item, init in onCreate()
     */
    lateinit var currentItem: NavigationItem

    private lateinit var navigationAdapter: ArrayAdapter<NavigationAdapterItem>

    private var drawerToggle: ActionBarDrawerToggle? = null

    enum class DrawerState { NONE, PLAYER, BROWSE }

    private var playerDrawerOpen = false
    private var browseDrawerOpen = false
    private var appliedDrawerState: DrawerState? = null

    private var searchMenuItem: MenuItem? = null

    protected lateinit var drawerBinding: DrawerBinding
    protected lateinit var toolbarBinding: ToolbarBinding

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drawerBinding = DrawerBinding.inflate(layoutInflater)
        toolbarBinding = ToolbarBinding.bind(drawerBinding.root)
        contentView = drawerBinding.root

        drawerBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GRAVITY_BROWSE_DRAWER)
        drawerBinding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GRAVITY_PLAYER_DRAWER)

        drawerToggle = if (enableDrawerToggle()) {
            ActionBarDrawerToggle(this, drawerBinding.drawerLayout, toolbarBinding.toolbar, R.string.drawer_open, R.string.drawer_close)
        } else {
            null
        }

        navigationAdapter = ArrayAdapter<NavigationAdapterItem>(this, android.R.layout.simple_spinner_item, android.R.id.text1)
                .apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }

        navigationManager = NavigationManager(this)

        drawerBinding.drawerLayout.addDrawerListener(object : SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (drawerView.id == R.id.browse_drawer) {
                    drawerToggle?.onDrawerSlide(drawerView, slideOffset)
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                drawerToggle?.onDrawerOpened(drawerView)

                if (drawerView.id == R.id.browse_drawer) {
                    browseDrawerOpen = true
                    playerDrawerOpen = false
                    drawerBinding.drawerLayout.closeDrawer(GRAVITY_PLAYER_DRAWER)
                } else {
                    playerDrawerOpen = true
                    browseDrawerOpen = false
                    drawerBinding.drawerLayout.closeDrawer(GRAVITY_BROWSE_DRAWER)
                }
                doApplyDrawerState()
            }

            override fun onDrawerClosed(drawerView: View) {
                drawerToggle?.onDrawerClosed(drawerView)
                if (drawerView.id == R.id.browse_drawer) {
                    browseDrawerOpen = false
                } else {
                    playerDrawerOpen = false
                }
                doApplyDrawerState()
            }

            override fun onDrawerStateChanged(newState: Int) {
                drawerToggle?.onDrawerStateChanged(newState)
            }
        })

        supportActionBar.navigationMode = ActionBar.NAVIGATION_MODE_LIST
        supportActionBar.setDisplayShowTitleEnabled(false)

        if (savedInstanceState == null) {
            currentItem = NavigationItem.getNavigationItem(intent)
                    ?: NavigationItem.newFixedItem(this, NavigationItem.Type.HOME)
            navigationStack = navigationManager.getNavigationStack(intent)

            refreshNavigationList()

            // load item after onCreate() is complete so that all views are available, etc
            AndroidSchedulers.mainThread().scheduleDirect {
                try {
                    onLoadNavigationItem(currentItem)
                } catch (e: IllegalStateException) {
                    // ignore
                }
            }
        } else {
            // when we restore, a fragment is already created and the nav index is restored with the spinner state
            // but an event will trigger, so ignore that one
            currentItem = checkNotNull(savedInstanceState.getParcelable(STATE_NAV_ITEM))
            { "saved item shouldn't be null: $savedInstanceState" }

            navigationStack = checkNotNull(savedInstanceState.getParcelableArrayList(STATE_NAV_STACK))
            { "nav stack shouldn't be null: $savedInstanceState" }

            refreshNavigationList()
        }
    }

    override fun onStart() {
        super.onStart()

        title = currentItem.name
    }

    override fun getSupportActionBar(): ActionBar {
        return requireNotNull(super.getSupportActionBar())
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        initToolbar()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)

        initToolbar()
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        super.setContentView(view, params)

        initToolbar()
    }

    private fun initToolbar() {
        setSupportActionBar(toolbarBinding.toolbar)
    }

    fun closeDrawers() {
        drawerBinding.drawerLayout.closeDrawers()
    }

    fun expandSearchActionView() {
        searchMenuItem?.expandActionView()
    }

    fun collapseSearchActionView() {
        searchMenuItem?.collapseActionView()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        drawerToggle?.syncState()

        doApplyDrawerState()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val retval = super.onCreateOptionsMenu(menu)
        searchMenuItem = menu.findItem(R.id.menu_search)
        return retval
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        drawerToggle?.let {
            if (it.onOptionsItemSelected(item)) {
                return true
            }
        }
        return if (item.itemId == R.id.menu_players) {
            if (playerDrawerOpen) {
                playerDrawerOpen = false
                drawerBinding.drawerLayout.closeDrawer(GRAVITY_PLAYER_DRAWER)
            } else {
                playerDrawerOpen = true
                browseDrawerOpen = false

                // make sure browse drawer is closed
                drawerBinding.drawerLayout.closeDrawer(GRAVITY_BROWSE_DRAWER)

                drawerBinding.drawerLayout.openDrawer(GRAVITY_PLAYER_DRAWER)
            }
            doApplyDrawerState()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    protected open fun enableDrawerToggle(): Boolean {
        return false
    }

    protected fun doApplyDrawerState() {
        val state = when {
            playerDrawerOpen -> DrawerState.PLAYER
            browseDrawerOpen -> DrawerState.BROWSE
            else -> DrawerState.NONE
        }
        if (state != DrawerState.NONE) {
            searchMenuItem?.collapseActionView()
        }

        if (appliedDrawerState != state) {
            appliedDrawerState = state
            applyDrawerState(state)
        }
    }

    protected open fun applyDrawerState(drawerState: DrawerState) {
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        drawerToggle?.onConfigurationChanged(newConfig)
    }

    final override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        onNewIntentBehavior(intent)
    }

    protected open fun onNewIntentBehavior(intent: Intent) {
        val newItem = NavigationItem.getNavigationItem(intent)
        if (newItem != null) {
            currentItem = newItem
            refreshNavigationList()
            onLoadNavigationItem(newItem)
        }
    }

    protected open fun onLoadNavigationItem(navigationItem: NavigationItem) {
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(STATE_NAV_ITEM, currentItem)
        outState.putParcelableArrayList(STATE_NAV_STACK, ArrayList(navigationStack))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != NavigationManager.ACTIVITY_REQUESTCODE || data == null) {
            return
        }

        val targetLevel = when (resultCode) {
            NavigationManager.RESULT_NAVITEM_CHANGED -> {
                val item = checkNotNull(NavigationItem.getNavigationItem(data))
                if (item == currentItem) {
                    currentItemLevel
                } else {
                    navigationStack.indexOfFirst { it == item }
                }
            }
            NavigationManager.RESULT_GOTO_LEVEL -> {
                data.getIntExtra(NavigationManager.EXTRA_TARGET_LEVEL, -1)
            }
            else -> throw UnsupportedOperationException()
        }

        if (targetLevel >= 0 && targetLevel < (currentItemLevel - 1)) {
            // go up another level
            setResult(resultCode, data)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshNavigationList() {
        var navigationModeAsList = false

        navigationAdapter.clear()
        navigationAdapter.addAll(navigationStack.map { NavigationAdapterItem(it) })
        navigationAdapter.add(NavigationAdapterItem(currentItem))
        navigationAdapter.notifyDataSetChanged()

        // if there's a history in the navstack and this isn't the nowplaying....
        if (navigationStack.isNotEmpty() && currentItem.type != NavigationItem.Type.NOWPLAYING) {
            navigationModeAsList = true
        }

        supportActionBar.let { actionBar ->
            if (navigationModeAsList) {
                actionBar.setListNavigationCallbacks(navigationAdapter, navigationListener)
                actionBar.setSelectedNavigationItem(navigationAdapter.count - 1)
                actionBar.navigationMode = ActionBar.NAVIGATION_MODE_LIST
                actionBar.setDisplayShowTitleEnabled(false)
            } else {
                actionBar.navigationMode = ActionBar.NAVIGATION_MODE_STANDARD
                actionBar.setDisplayShowTitleEnabled(true)
            }
        }
    }

    @Suppress("DEPRECATION")
    private val navigationListener = ActionBar.OnNavigationListener { index, _ ->
        OSLog.d("nav index changed event $index")
        val newItem = checkNotNull(navigationAdapter.getItem(index)?.navigationItem) {
            "adapter should never return null"
        }

        var retval = false
        if (newItem != currentItem) {
            retval = true
            setResult(NavigationManager.RESULT_NAVITEM_CHANGED, NavigationManager.newNavigationIntent(this, newItem))
            finish()
        }

        retval
    }
}
