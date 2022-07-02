/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.app

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.annotation.GuardedBy
import androidx.preference.PreferenceManager
import com.orangebikelabs.orangesqueeze.R
import com.orangebikelabs.orangesqueeze.common.SBPreferences
import java.util.*


/**
 * This class is used to change your application locale and persist this change for the next time
 * that your app is going to be used.
 *
 * You can also change the locale of your application on the fly by using the setLocale method.
 */
object LocaleHelper {

    @GuardedBy("this")
    private var currentLanguage: String? = null

    fun onAttach(context: Context): Context {
        val lang = getCurrentLanguage(context, Locale.getDefault().language)
        return setLocale(context, lang)
    }

    fun getLanguage(context: Context): String {
        return getCurrentLanguage(context, Locale.getDefault().language)
    }

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)

        val locale = Locale(language)
        Locale.setDefault(locale)

        return LanguageContextWrapper.wrap(context, language)
    }

    @Synchronized
    private fun getCurrentLanguage(context: Context, defaultLanguage: String): String {
        var retval = currentLanguage
        if (retval == null) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            retval = checkNotNull(preferences.getString(SBPreferences.SELECTED_LANGUAGE_KEY, defaultLanguage))
            currentLanguage = retval
        }
        return retval
    }

    @Synchronized
    private fun persist(context: Context, language: String) {
        if (currentLanguage != language) {
            currentLanguage = language
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = preferences.edit()

            editor.putString(SBPreferences.SELECTED_LANGUAGE_KEY, language)
            editor.apply()
        }
    }

    class LanguageContextWrapper(base: Context) : ContextThemeWrapper(base, R.style.Theme_OrangeSqueeze) {
        companion object {
            fun wrap(context: Context, language: String): ContextWrapper {
                val config = context.resources.configuration

                if (getConfigurationLocale(config).language != language) {
                    val locale = Locale(language)
                    Locale.setDefault(locale)

                    setConfigurationLocale(config, locale)
                }

                return LanguageContextWrapper(context.createConfigurationContext(config))
            }

            private fun getConfigurationLocale(config: Configuration): Locale {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    config.locales.get(0)
                } else {
                    @Suppress("DEPRECATION")
                    config.locale
                }
            }

            private fun setConfigurationLocale(config: Configuration, locale: Locale) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    config.setLocale(locale)
                } else {
                    @Suppress("DEPRECATION")
                    config.locale = locale
                }
            }
        }

        override fun getBaseContext(): Context {
            var base = super.getBaseContext()
            while (base is ContextWrapper) {
                base = base.baseContext
            }
            return base
        }

        override fun applyOverrideConfiguration(overrideConfiguration: Configuration) {
            val newConfig = Configuration(overrideConfiguration)
            newConfig.setLocale(Locale.getDefault())
            super.applyOverrideConfiguration(newConfig)
        }
    }
}