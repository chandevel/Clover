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
import android.widget.FrameLayout;

import org.floens.chan.ui.toolbar.Toolbar;

import java.util.ArrayList;
import java.util.List;

public abstract class NavigationController extends Controller implements ControllerTransition.Callback, Toolbar.ToolbarCallback {
    public Toolbar toolbar;
    public FrameLayout container;

    private List<Controller> controllerList = new ArrayList<>();
    private ControllerTransition controllerTransition;
    private boolean blockingInput = false;

    public NavigationController(Context context) {
        super(context);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        while (controllerList.size() > 0) {
            popController(false);
        }
    }

    public boolean pushController(final Controller to) {
        return pushController(to, true);
    }

    public boolean pushController(final Controller to, boolean animated) {
        return pushController(to, animated ? new PushControllerTransition() : null);
    }

    public boolean pushController(final Controller to, ControllerTransition controllerTransition) {
        if (blockingInput) return false;

        if (this.controllerTransition != null) {
            throw new IllegalArgumentException("Cannot push controller while a transition is in progress.");
        }

        final Controller from = controllerList.size() > 0 ? controllerList.get(controllerList.size() - 1) : null;
        to.navigationController = this;
        to.previousSiblingController = from;

        controllerList.add(to);

        if (controllerTransition != null) {
            blockingInput = true;
            this.controllerTransition = controllerTransition;
            controllerTransition.setCallback(this);

            ControllerLogic.startTransition(from, to, false, true, container, controllerTransition);
            toolbar.setNavigationItem(true, true, to.navigationItem);
        } else {
            ControllerLogic.transition(from, to, false, true, container);
            toolbar.setNavigationItem(false, true, to.navigationItem);
        }

        controllerPushed(to);

        return true;
    }

    protected void controllerPushed(Controller controller) {
    }

    public boolean popController() {
        return popController(true);
    }

    public boolean popController(boolean animated) {
        return popController(animated ? new PopControllerTransition() : null);
    }

    public boolean popController(ControllerTransition controllerTransition) {
        if (blockingInput) return false;

        if (this.controllerTransition != null) {
            throw new IllegalArgumentException("Cannot pop controller while a transition is in progress.");
        }

        if (controllerList.size() == 0) {
            throw new IllegalArgumentException("Cannot pop with no controllers left");
        }

        final Controller from = controllerList.get(controllerList.size() - 1);
        final Controller to = controllerList.size() > 1 ? controllerList.get(controllerList.size() - 2) : null;

        if (controllerTransition != null) {
            blockingInput = true;
            this.controllerTransition = controllerTransition;
            controllerTransition.setCallback(this);

            ControllerLogic.startTransition(from, to, true, false, container, controllerTransition);
            if (to != null) {
                toolbar.setNavigationItem(true, false, to.navigationItem);
            }
        } else {
            ControllerLogic.transition(from, to, true, false, container);
            if (to != null) {
                toolbar.setNavigationItem(false, false, to.navigationItem);
            }
            controllerList.remove(from);
        }

        if (to != null) {
            controllerPopped(to);
        }

        return true;
    }

    protected void controllerPopped(Controller controller) {
    }

    public Controller getTop() {
        if (controllerList.size() > 0) {
            return controllerList.get(controllerList.size() - 1);
        } else {
            return null;
        }
    }

    /*
     * Used to save instance state
     */
    public List<Controller> getControllerList() {
        return controllerList;
    }

    @Override
    public void onControllerTransitionCompleted(ControllerTransition transition) {
        ControllerLogic.finishTransition(transition);

        if (transition.destroyFrom) {
            controllerList.remove(transition.from);
        }

        this.controllerTransition = null;
        blockingInput = false;
    }

    public boolean onBack() {
        if (blockingInput) return true;

        if (toolbar.closeSearch()) {
            return true;
        }

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
    }

    public void onMenuClicked() {

    }

    public void showSearch() {
        toolbar.openSearch();
    }

    @Override
    public void onMenuOrBackClicked(boolean isArrow) {
        if (isArrow) {
            onBack();
        } else {
            onMenuClicked();
        }
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
    }

    @Override
    public String getSearchHint() {
        return "";
    }

    @Override
    public void onSearchEntered(String entered) {
    }
}
