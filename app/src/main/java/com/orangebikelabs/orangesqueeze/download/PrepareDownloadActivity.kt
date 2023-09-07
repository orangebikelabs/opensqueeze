/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */
package com.orangebikelabs.orangesqueeze.download

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.fragment.app.commitNow
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.app.SBActivity
import com.orangebikelabs.orangesqueeze.common.NavigationCommandSet
import com.orangebikelabs.orangesqueeze.common.NavigationItem
import com.orangebikelabs.orangesqueeze.databinding.ToolbarActivityBinding
import com.orangebikelabs.orangesqueeze.databinding.ToolbarBinding
import com.orangebikelabs.orangesqueeze.ui.MainActivity

/**
 * @author tsandee
 */
class PrepareDownloadActivity : SBActivity() {
    companion object {
        fun newIntent(context: Context, commands: List<String>, parameters: List<String>, downloadTitle: String): Intent {
            val intent = Intent(context, PrepareDownloadActivity::class.java)
            val ncs = NavigationCommandSet(commands, parameters)
            val item = NavigationItem.newPrepareDownloadItem(downloadTitle, ncs)
            NavigationItem.putNavigationItem(intent, item)
            return intent
        }
    }

    // avoid use of lateinit because sometimes snackbar notice comes before onCreate() and it would crash
    private var binding: ToolbarActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ToolbarActivityBinding.inflate(layoutInflater).also {
            setContentView(it.root)

            val toolbarBinding = ToolbarBinding.bind(it.root)
            setSupportActionBar(toolbarBinding.toolbar)
        }

        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        val item = checkNotNull(NavigationItem.getNavigationItem(intent))
        val title = item.name
        supportActionBar?.title = getString(R.string.download_activity_title, title)
        if (savedInstanceState == null) {
            supportFragmentManager.commitNow {
                add(R.id.toolbar_content, PrepareDownloadFragment.newInstance(item))
            }
        }
    }

    override fun getSnackbarView(): View? {
        return binding?.toolbarContent
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
}