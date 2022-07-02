/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import com.orangebikelabs.orangesqueeze.app.ConnectionAwareRunnable
import com.orangebikelabs.orangesqueeze.app.ServerConnectionService
import com.orangebikelabs.orangesqueeze.common.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Base class for our widgets.
 *
 * @author tsandee
 */
abstract class AbsWidget : AppWidgetProvider() {

    private var initialUpdateDisposable: Disposable? = null

    private var commandUpdateDisposable: Disposable? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        OSLog.i(javaClass.simpleName + "::onUpdate")

        // only launch service on releases prior to Android O
        if (Build.VERSION.SDK_INT < 26 && !ServerConnectionService.serviceWasStopped) {
            onUpdateLegacy(context)
        } else {
            onUpdateModern(context)
        }
    }

    private fun onUpdateModern(context: Context) {
        val sbContext = SBContextProvider.initializeAndGet(context)
        val pendingResult = goAsync()

        sbContext.temporaryOnStart(context, 10, TimeUnit.SECONDS)

        initialUpdateDisposable?.dispose()
        initialUpdateDisposable = OSExecutors.singleThreadScheduler().scheduleDirect {
            // update immediately to reflect what's happening
            OSLog.d("initial update widget")
            WidgetCommon.updateWidgets(sbContext)
        }

        val runnable = ConnectionAwareRunnable(location = "onUpdateModern") {
            OSLog.d("secondary update widget")
            it.newRequest(SBRequest.Type.COMET, "serverstatus", "0", "1")
                    .submit(OSExecutors.getUnboundedPool())
                    .get(4, TimeUnit.SECONDS)
        }
        commandUpdateDisposable?.dispose()
        commandUpdateDisposable = Completable
                .fromRunnable(runnable)
                .subscribeOn(Schedulers.io())
                .retry(2)
                .doOnComplete { WidgetCommon.updateWidgets(sbContext) }
                .observeOn(AndroidSchedulers.mainThread())
                .timeout(8, TimeUnit.SECONDS)
                .doFinally {
                    pendingResult.finish()
                }
                .subscribeBy(
                        onError = { throwable ->
                            OSLog.w(throwable.message ?: "", throwable)
                        }
                )
    }

    private fun onUpdateLegacy(context: Context) {
        val intent = ServerConnectionService.getIntent(context, ServerConnectionService.ServiceActions.SERVICE_TO_BACKGROUND)
        context.startService(intent)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)

        setWidgetEnabled(javaClass, false)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        setWidgetEnabled(javaClass, true)
    }

    private fun setWidgetEnabled(clz: Class<out AbsWidget>, enabled: Boolean) {
        val prefs = SBPreferences.get()
        prefs.setWidgetEnabled(clz.simpleName, enabled)
    }
}
