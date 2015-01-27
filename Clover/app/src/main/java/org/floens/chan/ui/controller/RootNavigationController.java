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
            public void onMeasured(View view, int width, int height) {
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
