/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.menu;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class MenuHelpers {

    @Nullable
    static public String getNextWindow(MenuElement element, MenuAction action) {
        String retval = element.getNextWindow();
        if (retval == null) {
            retval = action.getNextWindow();
        }
        return retval;
    }

    /**
     * this routine's logic is pulled from SlimBrowserApplet.lua:_actionHandler
     */
    @SuppressWarnings("UnusedAssignment")
    @Nullable
    static public MenuAction getAction(MenuElement item, String actionName) {
        @SuppressWarnings("unused")
        MenuAction bAction, iAction, bActionContextMenu, iActionContextMenu, onAction, offAction, jsonAction;
        String iNextWindow, aNextWindow, nextWindow;

        iNextWindow = aNextWindow = nextWindow = null;
        bAction = iAction = bActionContextMenu = iActionContextMenu = onAction = offAction = jsonAction = null;

        MenuBase base = item.getMenuBase();
        if (base == null) {
            base = new MenuBase();
        }

        if (ActionNames.NONE.equals(item.getAction())) {
            // special case for do nothing
            return null;
        }

        iNextWindow = item.getNextWindow();

        @SuppressWarnings("unused")
        boolean useNextWindow = false;
        boolean actionHandlersExist = !base.getActions().isEmpty() && item.getActions().isEmpty();

        if ((iNextWindow != null) && !actionHandlersExist && ActionNames.GO.equals(actionName)) {
            useNextWindow = true;
        }

        String potentialActionAlias = null;
        // first look for translated action names, if they exist
        if (actionName.equals(ActionNames.GO) && item.getGoAction() != null) {
            potentialActionAlias = item.getGoAction();
        } else if (actionName.equals(ActionNames.ADD) && item.getAddAction() != null) {
            potentialActionAlias = item.getAddAction();
        } else if (actionName.equals(ActionNames.PLAY) && item.getPlayAction() != null) {
            potentialActionAlias = item.getPlayAction();
        } else if (actionName.equals(ActionNames.PLAYHOLD) && item.getPlayHoldAction() != null) {
            potentialActionAlias = item.getPlayHoldAction();
        }
        if (potentialActionAlias != null && (item.getActions().containsKey(potentialActionAlias) || item.getMenuBase().getActions().containsKey(potentialActionAlias))) {
            actionName = potentialActionAlias;
        }

        if (ActionNames.GO.equals(actionName)) {
            // skip hierarchical menu or a input to perform check

            // skip local service calls

            // check for 'do' action

            bAction = base.getActions().get(ActionNames.DO);
            iAction = item.getActions().get(ActionNames.DO);
            onAction = item.getActions().get(ActionNames.ON);
            offAction = item.getActions().get(ActionNames.OFF);
        } else {
            // skip special action handler for alarm sounds
        }

        // set up context menu flag
        @SuppressWarnings("unused")
        boolean isContextMenu = false;

        // these are intentionally (maybe) different from bAction, iAction!
        MenuAction localItemAction = item.getActions().get(actionName);
        MenuAction localBaseAction = base.getActions().get(actionName);

        if (localItemAction != null && localItemAction.isContextMenu()) {
            isContextMenu = true;
        } else if (localBaseAction != null && localBaseAction.isContextMenu()) {
            isContextMenu = true;
        }

        MenuAction doAction = item.getActions().get(ActionNames.DO);
        boolean choiceAction = doAction != null && !doAction.getChoices().isEmpty();

        if (!(iAction != null || bAction != null || onAction != null || offAction != null || choiceAction)) {
            bAction = base.getActions().get(actionName);
            iAction = item.getActions().get(actionName);
        } else if (actionName.equals(ActionNames.PREVIEW)) {
            // do nothing? skip
        } else {
            actionName = ActionNames.DO;
        }

        // reset these to updated action name
        localItemAction = item.getActions().get(actionName);
        localBaseAction = base.getActions().get(actionName);

        // set "action" next window
        if (localItemAction != null && localItemAction.getNextWindow() != null) {
            aNextWindow = localItemAction.getNextWindow();
        } else if (localBaseAction != null && localBaseAction.getNextWindow() != null) {
            aNextWindow = localBaseAction.getNextWindow();
        }

        // action next window priority = 1, item next window priority = 2, base next window priority = 3
        if (aNextWindow != null) {
            nextWindow = aNextWindow;
        } else if (iNextWindow != null) {
            nextWindow = iNextWindow;
        }

        // skip set selected index handling

        if (iAction != null || bAction != null || choiceAction || nextWindow != null) {
            // skip caching of more callback for next window
            if (iAction != null) {
                jsonAction = iAction;
            } else if (bAction != null) {
                jsonAction = bAction;
            }
        }
        return jsonAction;
    }

    @Nullable
    static public List<String> buildSearchParametersAsList(MenuElement element, MenuAction action, String searchSpec, boolean window) {
        Map<String, Object> params = buildParameters(element, action, window);
        if (params == null) {
            return null;
        }

        ArrayList<String> retval = Lists.newArrayListWithExpectedSize(params.size());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object val = entry.getValue();
            if (val != null) {
                String addValue = entry.getKey() + ":" + val;
                if (val.equals("__TAGGEDINPUT__")) {
                    addValue = entry.getKey() + ":" + searchSpec;
                } else if (val.equals("__INPUT__")) {
                    addValue = searchSpec;
                }
                retval.add(addValue);
            }
        }
        return retval;
    }

    @Nullable
    static public ArrayList<String> buildParametersAsList(MenuElement element, MenuAction action, boolean window) {
        Map<String, Object> params = buildParameters(element, action, window);
        if (params == null) {
            return null;
        }

        ArrayList<String> retval = Lists.newArrayListWithExpectedSize(params.size());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                retval.add(entry.getKey() + ":" + entry.getValue());
            }
        }
        return retval;
    }

    // preserve object ordering, use LinkedHashMap
    @Nullable
    static public LinkedHashMap<String, Object> buildItemParameters(MenuElement element, MenuAction action) {
        LinkedHashMap<String, Object> retval;

        LinkedHashMap<String, Object> actionParams = new LinkedHashMap<>(action.getParams());

        String paramName = action.getItemsParams();
        if (paramName != null) {
            Map<String, Object> iParams = element.getNamedParams(paramName);
            if (iParams != null) {
                retval = actionParams;
                retval.putAll(iParams);
            } else {
                if (element.getActions().containsValue(action) && paramName.equals("params")) {
                    // just the params from the action is okay
                    retval = actionParams;
                } else {
                    // missing the params object, invalid item
                    retval = null;
                }
            }
        } else {
            retval = actionParams;
        }
        return retval;
    }

    @Nullable
    static public LinkedHashMap<String, Object> buildParameters(MenuElement element, MenuAction action, boolean window) {
        LinkedHashMap<String, Object> retval = buildItemParameters(element, action);
        if (retval == null) {
            return null;
        }

        boolean isContextMenu = false;
        String value = action.getWindow().get("isContextMenu");
        if (value != null) {
            try {
                isContextMenu = Integer.parseInt(value) != 0;
            } catch (NumberFormatException e) {
                // ignore, isContextMenu=false then
            }
        }
        if (window) {
            retval.putAll(action.getWindow());
        }

        if(!action.getParams().isEmpty()) {
            retval.put("useContextMenu", "1");
        }

        // this logic is from SqueezePlay
        if (isContextMenu || ActionNames.MORE.equals(action.getName())
                || (ActionNames.ADD.equals(action.getName()) && ActionNames.MORE.equals(element.getAddAction()))) {
            retval.put("xmlBrowseInterimCM", "1");
        }
        return retval;
    }
}
