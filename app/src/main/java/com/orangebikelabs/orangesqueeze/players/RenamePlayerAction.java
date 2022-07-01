/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.content.Context;
import androidx.fragment.app.Fragment;

import com.orangebikelabs.orangesqueeze.R;

/**
 * @author tbsandee@orangebikelabs.com
 */
public class RenamePlayerAction extends AbsPlayerAction {

  public RenamePlayerAction(Context context) {
    super(context, R.string.action_renameplayer, R.drawable.ic_edit);
  }

  @Override
  public boolean execute(Fragment controller) {
    ((ManagePlayersFragment) controller).control_renamePlayer(mPlayerId);
    return true;
  }
}
