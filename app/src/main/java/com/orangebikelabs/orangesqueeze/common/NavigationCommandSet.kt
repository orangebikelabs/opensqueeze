/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.common

import android.os.Parcelable
import com.orangebikelabs.orangesqueeze.menu.MenuAction
import com.orangebikelabs.orangesqueeze.menu.MenuElement
import kotlinx.parcelize.Parcelize
import java.util.LinkedHashMap

/**
 * Represents a commandset, typically to be stored with a navigation item. Examples include request,
 * play, add events.
 */
@Parcelize
data class NavigationCommandSet constructor(val commands: List<String>, val parameters: List<String>) : Parcelable {
    constructor(command: String, vararg params: String) : this(listOf(command), params.toList())
    constructor(commands: List<String>, vararg params: String) : this(commands, params.toList())

    companion object {
        fun buildCommandSet(elem: MenuElement, actionName: String): NavigationCommandSet? {
            var retval: NavigationCommandSet? = null
            var action = elem.actions[actionName]
            if (action == null) {
                action = elem.baseActions[actionName]
            }
            if (action != null) {
                val params = buildParametersAsList(elem, action)
                if (params != null) {
                    retval = NavigationCommandSet(action.commands, params)
                }
            }
            return retval
        }

        private fun buildParametersAsList(element: MenuElement, action: MenuAction): List<String>? {
            val params = buildItemParameters(element, action)
                    ?: return null
            return params
                    .filter { it.value != null }
                    .map {
                        "${it.key}:${it.value}"
                    }
        }

        // preserve object ordering, use LinkedHashMap
        private fun buildItemParameters(element: MenuElement, action: MenuAction): LinkedHashMap<String, Any?>? {
            val retval: LinkedHashMap<String, Any?>?

            val actionParams = LinkedHashMap<String, Any?>(action.params)

            val paramName = action.itemsParams
            if (paramName != null) {
                val iParams = element.getNamedParams(paramName)
                if (iParams != null) {
                    retval = actionParams
                    retval.putAll(iParams)
                } else {
                    if (element.actions.containsValue(action) && paramName == "params") {
                        // just the params from the action is okay
                        retval = actionParams
                    } else {
                        // missing the params object, invalid item
                        retval = null
                    }
                }
            } else {
                retval = actionParams
            }
            return retval
        }
    }
}
