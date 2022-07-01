/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
 */

package com.orangebikelabs.orangesqueeze.players;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.orangebikelabs.orangesqueeze.R;
import com.orangebikelabs.orangesqueeze.app.DrawerActivity;
import com.orangebikelabs.orangesqueeze.common.NavigationItem;

import javax.annotation.Nullable;

import androidx.annotation.Keep;

/**
 * @author tbsandee@orangebikelabs.com
 */
@Keep
public class PlayerDrawerFragment extends ManagePlayersFragment {

    private ImageButton mManagePlayersButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // override this -- we don't want the option menu for this fragment to be impacting things
        setHasOptionsMenu(false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAdapter.setDrawerMode(true);

        mManagePlayersButton = view.findViewById(R.id.manage_players_button);
        mManagePlayersButton.setOnClickListener(v -> {
            NavigationItem ni = NavigationItem.Companion.newFixedItem(requireContext(), NavigationItem.Type.PLAYERS);
            getNavigationManager().navigateTo(ni);
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.manageplayers_drawer, container, false);
    }

    @Override
    protected void onListItemClick(View v, int position, long id) {
        super.onListItemClick(v, position, id);

        DrawerActivity activity = (DrawerActivity) requireActivity();
        activity.closeDrawers();
    }
}
