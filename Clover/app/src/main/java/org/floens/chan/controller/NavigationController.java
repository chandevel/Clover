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
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.floens.chan.ui.toolbar.Toolbar;

import java.util.ArrayList;
import java.util.List;

public abstract class NavigationController extends Controller implements Toolbar.ToolbarCallback, ControllerTransition.Callback {
    protected Toolbar toolbar;
    protected ViewGroup container;

    protected List<Controller> controllerList = new ArrayList<>();
    protected ControllerTransition controllerTransition;
    protected boolean blockingInput = false;

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

        final Controller from = controllerList.size() > 0 ? controllerList.get(controllerList.size() - 1) : null;
        to.navigationController = this;
        to.previousSiblingController = from;

        controllerList.add(to);

        transition(from, to, true, controllerTransition);

        return true;
    }

    public boolean popController() {
        return popController(true);
    }

    public boolean popController(boolean animated) {
        return popController(animated ? new PopControllerTransition() : null);
    }

    public boolean popController(ControllerTransition controllerTransition) {
        if (blockingInput) return false;

        final Controller from = controllerList.get(controllerList.size() - 1);
        final Controller to = controllerList.size() > 1 ? controllerList.get(controllerList.size() - 2) : null;

        transition(from, to, false, controllerTransition);

        return true;
    }

    public void transition(Controller from, Controller to, boolean pushing, ControllerTransition controllerTransition) {
        if (this.controllerTransition != null) {
            throw new IllegalArgumentException("Cannot transition while another transition is in progress.");
        }

        if (!pushing && controllerList.size() == 0) {
            throw new IllegalArgumentException("Cannot pop with no controllers left");
        }

        if (controllerTransition != null) {
            blockingInput = true;
            this.controllerTransition = controllerTransition;
            controllerTransition.setCallback(this);
            ControllerLogic.startTransition(from, to, pushing, container, controllerTransition);
        } else {
            ControllerLogic.transition(from, to, pushing, container);
            if (!pushing) {
                controllerList.remove(from);
            }
        }

        if (to != null) {
            if (toolbar != null) {
                toolbar.setNavigationItem(controllerTransition != null, pushing, to.navigationItem);
            }

            updateToolbarCollapse(to, controllerTransition != null);

            if (pushing) {
                controllerPushed(to);
            } else {
                controllerPopped(to);
            }
        }
    }

    protected void controllerPushed(Controller controller) {
    }

    protected void controllerPopped(Controller controller) {
    }

    @Override
    public void onControllerTransitionCompleted(ControllerTransition transition) {
        ControllerLogic.finishTransition(transition);

        if (transition.destroyFrom) {
            controllerList.remove(transition.from);
        }

        controllerTransition = null;
        blockingInput = false;
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

    public Toolbar getToolbar() {
        return toolbar;
    }

    public boolean onBack() {
        if (blockingInput) return true;

        if (toolbar != null && toolbar.closeSearch()) {
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
        if (toolbar != null) {
            toolbar.openSearch();
        }
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

    protected void updateToolbarCollapse(Controller controller, boolean animate) {
        if (toolbar != null) {
            if (!controller.navigationItem.collapseToolbar) {
                FrameLayout.LayoutParams toViewParams = (FrameLayout.LayoutParams) controller.view.getLayoutParams();
                toViewParams.topMargin = toolbar.getToolbarHeight();
                controller.view.setLayoutParams(toViewParams);
            }

            toolbar.processScrollCollapse(Toolbar.TOOLBAR_COLLAPSE_SHOW, animate);
        }
    }
}
