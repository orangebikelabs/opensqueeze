/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.browse.common;

import android.content.Context;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.common.SBRequest;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import androidx.core.text.HtmlCompat;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class CommandTools {
    @Nullable
    static public CharSequence lookupToast(Context context, SBRequest request, String title) {
        CharSequence retval = null;
        List<?> commands = request.getCommands();
        String firstLine = title.split("\n")[0];

        if (
                commandsContain(commands, "playlistcontrol", "cmd:load")
                        || commandsContainInOrder(commands, "playlist", "play")
                        || commandsContain(commands, "trackstat", "trackstatcmd:play")
                        || commandsContainInOrder(commands, "custombrowse", "play")
        ) {
            retval = HtmlCompat.fromHtml(context.getString(R.string.playnow_action_toast_html, firstLine), 0);
        } else if (
                commandsContain(commands, "playlistcontrol", "cmd:insert")
                        || commandsContainInOrder(commands, "playlist", "insert")
                        || commandsContain(commands, "trackstat", "trackstatcmd:insert")
                        || commandsContainInOrder(commands, "custombrowse", "insert")
        ) {
            retval = HtmlCompat.fromHtml(context.getString(R.string.playnext_action_toast_html, firstLine), 0);
        } else if (
                commandsContain(commands, "playlistcontrol", "cmd:add")
                        || commandsContainInOrder(commands, "playlist", "add")
                        || commandsContain(commands, "trackstat", "trackstatcmd:add")
                        || commandsContainInOrder(commands, "custombrowse", "add")
        ) {
            retval = HtmlCompat.fromHtml(context.getString(R.string.addtoplaylist_action_toast_html, firstLine), 0);
        } else if (commandsContain(commands, "favorites", "add")) {
            retval = HtmlCompat.fromHtml(context.getString(R.string.addtofavorites_action_toast_html, firstLine), 0);
        } else if (commandsContain(commands, "favorites", "delete")) {
            retval = HtmlCompat.fromHtml(context.getString(R.string.removefavorite_action_toast_html, firstLine), 0);
        } else {
            if (!Strings.isNullOrEmpty(title)) {
                retval = title;
            }
        }
        return retval;
    }

    static public boolean commandsContainInOrder(List<?> commands, String... containsArray) {
        int ndx = commands.indexOf(containsArray[0]);
        if (ndx == -1) return false;

        List<?> subarray = commands.subList(ndx, ndx + containsArray.length);
        return commandMatches(subarray, containsArray);
    }

    static public boolean commandsContain(List<?> commands, String... containsArray) {
        for (String s : containsArray) {
            if (!commands.contains(s)) {
                return false;
            }
        }
        return true;
    }

    static public String commandAsString(List<?> commands) {
        // sometimes commands include nulls for some reason
        return Joiner.on(" ").skipNulls().join(commands);
    }

    public static boolean commandMatches(List<?> commands, String... matchesArray) {
        List<String> m = Arrays.asList(matchesArray);
        return commands.equals(m);
    }

}
