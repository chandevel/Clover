/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.controller;

import android.content.Context;
import android.content.res.Configuration;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.ui.adapter.PinAdapter;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.utils.AndroidUtils;

import static org.floens.chan.utils.AndroidUtils.dp;

public class RootNavigationController extends NavigationController {
    public DrawerLayout drawerLayout;
    public FrameLayout drawer;

    private RecyclerView recyclerView;

    public RootNavigationController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_navigation_drawer);
        toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        container = (FrameLayout) view.findViewById(R.id.container);
        drawerLayout = (DrawerLayout) view.findViewById(R.id.drawer_layout);
        drawer = (FrameLayout) view.findViewById(R.id.drawer);
        recyclerView = (RecyclerView) view.findViewById(R.id.drawer_recycler_view);

        recyclerView.setHasFixedSize(true);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(linearLayoutManager);

        PinAdapter adapter = new PinAdapter();
        recyclerView.setAdapter(adapter);

        toolbar.setCallback(this);

        AndroidUtils.waitForMeasure(drawer, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                return setDrawerWidth();
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        AndroidUtils.waitForLayout(drawer, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                return setDrawerWidth();
            }
        });
    }

    @Override
    public void onMenuClicked() {
        super.onMenuClicked();

        drawerLayout.openDrawer(drawer);
    }

    @Override
    protected void controllerPushed(Controller controller) {
        super.controllerPushed(controller);
        setDrawerEnabled(controller.navigationItem.hasDrawer);
    }

    @Override
    protected void controllerPopped(Controller controller) {
        super.controllerPopped(controller);
        setDrawerEnabled(controller.navigationItem.hasDrawer);
    }

    private void setDrawerEnabled(boolean enabled) {
        drawerLayout.setDrawerLockMode(enabled ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
    }

    private boolean setDrawerWidth() {
        int width = Math.min(view.getWidth() - dp(56), dp(56) * 6);
        if (drawer.getWidth() != width) {
            drawer.getLayoutParams().width = width;
            drawer.requestLayout();
            return true;
        } else {
            return false;
        }
    }
}
