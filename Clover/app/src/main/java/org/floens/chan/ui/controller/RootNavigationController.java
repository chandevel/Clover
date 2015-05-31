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
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.ControllerTransition;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Pin;
import org.floens.chan.ui.adapter.PinAdapter;
import org.floens.chan.ui.helper.SwipeListener;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.utils.AndroidUtils;

import de.greenrobot.event.EventBus;

import static org.floens.chan.utils.AndroidUtils.dp;

public class RootNavigationController extends NavigationController implements PinAdapter.Callback {
    public DrawerLayout drawerLayout;
    public FrameLayout drawer;

    private RecyclerView recyclerView;
    private PinAdapter pinAdapter;

    public RootNavigationController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        EventBus.getDefault().register(this);

        view = inflateRes(R.layout.controller_navigation_drawer);
        toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        container = (FrameLayout) view.findViewById(R.id.container);
        drawerLayout = (DrawerLayout) view.findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.LEFT);
        drawer = (FrameLayout) view.findViewById(R.id.drawer);
        recyclerView = (RecyclerView) view.findViewById(R.id.drawer_recycler_view);
        recyclerView.setHasFixedSize(true);

        pinAdapter = new PinAdapter(this);
        recyclerView.setAdapter(pinAdapter);

        new SwipeListener(context, recyclerView, pinAdapter);

        pinAdapter.onPinsChanged(Chan.getWatchManager().getPins());

        toolbar.setCallback(this);

        AndroidUtils.waitForMeasure(drawer, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                return setDrawerWidth();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(this);
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
    public boolean onBack() {
        if (drawerLayout.isDrawerOpen(drawer)) {
            drawerLayout.closeDrawer(drawer);
            return true;
        } else {
            return super.onBack();
        }
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

    @Override
    public void onControllerTransitionCompleted(ControllerTransition transition) {
        super.onControllerTransitionCompleted(transition);
        updateHighlighted();
    }

    public void updateHighlighted() {
        pinAdapter.updateHighlighted(recyclerView);
    }

    @Override
    public void onPinClicked(Pin pin) {
        Controller top = getTop();
        if (top instanceof DrawerCallbacks) {
            ((DrawerCallbacks) top).onPinClicked(pin);
            drawerLayout.closeDrawer(Gravity.LEFT);
            pinAdapter.updateHighlighted(recyclerView);
        }
    }

    public boolean isHighlighted(Pin pin) {
        Controller top = getTop();
        if (top instanceof DrawerCallbacks) {
            return ((DrawerCallbacks) top).isPinCurrent(pin);
        }
        return false;
    }

    @Override
    public void onWatchCountClicked(Pin pin) {
        Chan.getWatchManager().toggleWatch(pin);
    }

    @Override
    public void onHeaderClicked(PinAdapter.HeaderHolder holder) {
        pushController(new WatchSettingsController(context));
    }

    @Override
    public void openSettings() {
        pushController(new MainSettingsController(context));
    }

    public void onEvent(WatchManager.PinAddedMessage message) {
        pinAdapter.onPinAdded(message.pin);
        drawerLayout.openDrawer(drawer);
    }

    public void onEvent(WatchManager.PinRemovedMessage message) {
        pinAdapter.onPinRemoved(message.pin);
    }

    public void onEvent(WatchManager.PinChangedMessage message) {
        pinAdapter.onPinChanged(recyclerView, message.pin);
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

    @Override
    public String getSearchHint() {
        return context.getString(R.string.search_hint);
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        Controller top = getTop();
        if (top instanceof DrawerCallbacks) {
            ((DrawerCallbacks) top).onSearchVisibilityChanged(visible);
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        Controller top = getTop();
        if (top instanceof DrawerCallbacks) {
            ((DrawerCallbacks) top).onSearchEntered(entered);
        }
    }

    public interface DrawerCallbacks {
        void onPinClicked(Pin pin);

        boolean isPinCurrent(Pin pin);

        void onSearchVisibilityChanged(boolean visible);

        void onSearchEntered(String entered);
    }
}
