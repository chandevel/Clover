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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.utils.AndroidUtils;

import static org.floens.chan.utils.AndroidUtils.dp;

public class RootNavigationController extends NavigationController {
    public RootNavigationController(Context context, Controller startController) {
        super(context, startController);

        view = inflateRes(R.layout.root_layout);
        toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        container = (FrameLayout) view.findViewById(R.id.container);
        drawerLayout = (DrawerLayout) view.findViewById(R.id.drawer_layout);
        drawer = (FrameLayout) view.findViewById(R.id.drawer);

        toolbar.setCallback(this);

        initWithController(startController);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        AndroidUtils.waitForLayout(drawer, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public void onMeasured(View view) {
                setDrawerWidth();
            }
        });
    }

    @Override
    public void onCreate() {
        setDrawerWidth();
    }

    @Override
    public void onMenuClicked() {
        super.onMenuClicked();

        drawerLayout.openDrawer(drawer);
    }

    private void setDrawerWidth() {
        int width = Math.min(view.getWidth() - dp(56), dp(56) * 6);
        if (drawer.getWidth() != width) {
            ViewGroup.LayoutParams params = drawer.getLayoutParams();
            params.width = width;
            drawer.setLayoutParams(params);
        }
    }
}
