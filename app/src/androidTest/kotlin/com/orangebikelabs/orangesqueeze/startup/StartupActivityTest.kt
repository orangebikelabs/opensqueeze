/*
 * Copyright (c) 2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.startup

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.filters.SmallTest
import org.junit.Test

import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import com.orangebikelabs.orangesqueeze.R
import org.junit.Before

@RunWith(AndroidJUnit4::class)
@SmallTest
class StartupActivityTest {
    @Before
    fun init() {
        Intents.init()
    }

    @Test
    fun launchesIntoConnectActivity() {
        ActivityScenario.launch(StartupActivity::class.java).use {
            intended(hasComponent(ConnectActivity::class.java.name))
            onView(withId(R.id.discovery_toggle)).check(matches(isDisplayed()))
        }
    }
}