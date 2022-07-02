/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.Looper
import android.os.StrictMode
import androidx.multidex.MultiDex
import com.orangebikelabs.orangesqueeze.BuildConfig
import com.orangebikelabs.orangesqueeze.common.OSLog
import com.orangebikelabs.orangesqueeze.common.SBPreferences
import com.orangebikelabs.orangesqueeze.net.DeviceConnectivity
import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException


class SBApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // initialize logging for app
        OSLog.init(this)

        //OSLog.enableDebugLogging()

        // initialize rxjava error handler
        RxJavaPlugins.setErrorHandler { originalException ->
            var e: Throwable? = originalException
            if (e is UndeliverableException) {
                e = e.cause
            }
            if (e is IOException || e is SocketException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (e is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (e is NullPointerException || e is IllegalArgumentException) {
                // that's likely a bug in the application
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            if (e is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            OSLog.w("Undeliverable exception received, not sure what to do", e)
        }

        RxAndroidPlugins.setInitMainThreadSchedulerHandler {
            AndroidSchedulers.from(Looper.getMainLooper(), true)
        }

        // strict mode for debug apps
        if (BuildConfig.DEBUG) {
            StrictMode.enableDefaults()
            val vmPolicyBuilder = StrictMode.VmPolicy.Builder()

            vmPolicyBuilder
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()

            if (Build.VERSION.SDK_INT >= 26) {
                vmPolicyBuilder
                        .detectContentUriWithoutPermission()
                        .detectFileUriExposure()
            }
            vmPolicyBuilder.penaltyLog()

            StrictMode.setVmPolicy(vmPolicyBuilder.build())
        }

        // make sure that prefs are upgraded, etc but do it in background
        SBPreferences.asyncInitialize(this)
        DeviceConnectivity.init(this)
    }

    override fun attachBaseContext(base: Context) {
        val newBase = LocaleHelper.onAttach(base)

        super.attachBaseContext(newBase)

        if (BuildConfig.DEBUG) {
            MultiDex.install(this)
        }
    }

    override fun getBaseContext(): Context {
        var base = super.getBaseContext()
        while (base is ContextWrapper) {
            base = base.baseContext
        }
        return base
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        LocaleHelper.onAttach(this)
    }
}
