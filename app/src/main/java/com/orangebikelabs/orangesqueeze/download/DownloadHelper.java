/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the license that can be found in the LICENSE file.
 */

package com.orangebikelabs.orangesqueeze.download;

import com.orangebikelabs.orangesqueeze.browse.common.CommandTools;

import java.util.List;

/**
 * @author tsandee
 */
public class DownloadHelper {

    static public boolean isNestableRequest(List<String> commands) {
        boolean retval = false;
        if (CommandTools.commandMatches(commands, "browselibrary", "items")) {
            retval = true;
        } else if (CommandTools.commandsContain(commands, "custombrowse", "browsejive")) {
            retval = true;
        } else if (CommandTools.commandsContain(commands, "trackstat", "statisticsjive")) {
            retval = true;
        }
        return retval;
    }

}
