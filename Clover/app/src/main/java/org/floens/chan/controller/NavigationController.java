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
package org.floens.chan.controller;

import android.content.Context;
import android.content.res.Configuration;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.widget.FrameLayout;

import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class NavigationController extends Controller implements ControllerTransition.Callback, Toolbar.ToolbarCallback {
    public Toolbar toolbar;
    public FrameLayout container;
    public DrawerLayout drawerLayout;
    public FrameLayout drawer;

    private List<Controller> controllerList = new ArrayList<>();
    private ControllerTransition controllerTransition;
    private boolean blockingInput = true;

    public NavigationController(Context context, final Controller startController) {
        super(context);
    }

    public boolean pushController(final Controller to) {
        if (blockingInput) return false;

        if (this.controllerTransition != null) {
            throw new IllegalArgumentException("Cannot push controller while a transition is in progress.");
        }

        blockingInput = true;

        final Controller from = controllerList.get(controllerList.size() - 1);

        to.stackSiblingController = from;
        to.navigationController = this;
        to.onCreate();

        controllerList.add(to);

        this.controllerTransition = new PushControllerTransition();
        container.addView(to.view, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        AndroidUtils.waitForMeasure(to.view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public void onMeasured(View view, int width, int height) {
                to.onShow();

                doTransition(true, from, to, controllerTransition);
            }
        });

        return true;
    }

    public boolean popController() {
        if (blockingInput) return false;

        if (this.controllerTransition != null) {
            throw new IllegalArgumentException("Cannot pop controller while a transition is in progress.");
        }

        if (controllerList.size() == 1) {
            throw new IllegalArgumentException("Cannot pop with 1 controller left");
        }

        blockingInput = true;

        final Controller from = controllerList.get(controllerList.size() - 1);
        final Controller to = controllerList.get(controllerList.size() - 2);

        this.controllerTransition = new PopControllerTransition();
        container.addView(to.view, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        AndroidUtils.waitForMeasure(to.view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public void onMeasured(View view, int width, int height) {
                to.onShow();

                doTransition(false, from, to, controllerTransition);
            }
        });

        return true;
    }

    @Override
    public void onControllerTransitionCompleted() {
        if (controllerTransition instanceof PushControllerTransition) {
            controllerTransition.from.onHide();
            container.removeView(controllerTransition.from.view);
        } else if (controllerTransition instanceof PopControllerTransition) {
            controllerList.remove(controllerTransition.from);

            controllerTransition.from.onHide();
            container.removeView(controllerTransition.from.view);
            controllerTransition.from.onDestroy();
        }
        this.controllerTransition = null;
        blockingInput = false;
    }

    public boolean onBack() {
        if (blockingInput) return true;

        if (controllerList.size() > 0) {
            Controller top = controllerList.get(controllerList.size() - 1);
            if (top.onBack()) {
                return true;
            } else {
                if (controllerList.size() > 1) {
                    popController();
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        for (Controller controller : controllerList) {
            controller.onConfigurationChanged(newConfig);
        }

        toolbar.onConfigurationChanged(newConfig);
    }

    public void initWithController(final Controller controller) {
        controllerList.add(controller);
        controller.navigationController = this;
        controller.onCreate();
        toolbar.setNavigationItem(false, true, controller.navigationItem);
        container.addView(controller.view, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

        AndroidUtils.waitForMeasure(controller.view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public void onMeasured(View view, int width, int height) {
                onCreate();
                onShow();

                controller.onShow();
                blockingInput = false;
            }
        });
    }

    public void onMenuClicked() {

    }

    private void doTransition(boolean pushing, Controller from, Controller to, ControllerTransition transition) {
        transition.setCallback(this);
        transition.from = from;
        transition.to = to;
        transition.perform();

        toolbar.setNavigationItem(true, pushing, to.navigationItem);
    }

    @Override
    public void onMenuBackClicked(boolean isArrow) {
        if (isArrow) {
            onBack();
        } else {
            onMenuClicked();
        }
    }
}
