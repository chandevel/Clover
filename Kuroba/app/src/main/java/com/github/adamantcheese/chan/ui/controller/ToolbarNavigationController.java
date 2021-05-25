/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.widget.FrameLayout;

import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.ControllerTransition;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;

public abstract class ToolbarNavigationController
        extends NavigationController
        implements Toolbar.ToolbarCallback {
    protected Toolbar toolbar;
    protected boolean requireSpaceForToolbar = true;

    public ToolbarNavigationController(Context context) {
        super(context);
    }

    @Override
    public Toolbar getToolbar() {
        return toolbar;
    }

    public void showSearch() {
        toolbar.openSearch();
    }

    @Override
    public void transition(Controller from, Controller to, boolean pushing, ControllerTransition controllerTransition) {
        super.transition(from, to, pushing, controllerTransition);

        if (to != null) {
            toolbar.setNavigationItem(controllerTransition != null, pushing, to.navigation, null);
            updateToolbarCollapse(to, controllerTransition != null);
        }
    }

    @Override
    public boolean beginSwipeTransition(Controller from, Controller to) {
        if (!super.beginSwipeTransition(from, to)) {
            return false;
        }

        toolbar.processScrollCollapse(Toolbar.TOOLBAR_COLLAPSE_SHOW, true);
        toolbar.beginTransition(to.navigation);
        toolbar.transitionProgress(0f);

        return true;
    }

    @Override
    public void swipeTransitionProgress(float progress) {
        super.swipeTransitionProgress(progress);

        toolbar.transitionProgress(progress);
    }

    @Override
    public void endSwipeTransition(Controller from, Controller to, boolean finish) {
        super.endSwipeTransition(from, to, finish);

        toolbar.finishTransition(finish);
        updateToolbarCollapse(finish ? to : from, controllerTransition != null);
    }

    @Override
    public void onMenuOrBackClicked(boolean isArrow) {
        if (isArrow) {
            onBack();
        } else {
            onMenuClicked();
        }
    }

    public void onMenuClicked() {
    }

    @Override
    public boolean onBack() {
        return toolbar.closeSearch() || super.onBack();
    }

    @Override
    public void onSearchVisibilityChanged(NavigationItem item, boolean visible) {
        for (Controller controller : childControllers) {
            if (controller.navigation == item && controller instanceof ToolbarSearchCallback) {
                ((ToolbarSearchCallback) controller).onSearchVisibilityChanged(visible);
                break;
            }
        }
    }

    @Override
    public void onSearchEntered(NavigationItem item, String entered) {
        for (Controller controller : childControllers) {
            if (controller.navigation == item && controller instanceof ToolbarSearchCallback) {
                ((ToolbarSearchCallback) controller).onSearchEntered(entered);
                break;
            }
        }
    }

    @Override
    public void onNavItemSet(NavigationItem item) {
        for (Controller controller : childControllers) {
            if (controller.navigation == item && controller instanceof ToolbarSearchCallback) {
                ((ToolbarSearchCallback) controller).onNavItemSet();
                break;
            }
        }
    }

    protected void updateToolbarCollapse(Controller controller, boolean animate) {
        if (requireSpaceForToolbar && !controller.navigation.handlesToolbarInset) {
            FrameLayout.LayoutParams toViewParams = (FrameLayout.LayoutParams) controller.view.getLayoutParams();
            toViewParams.topMargin = toolbar.getToolbarHeight();
            controller.view.setLayoutParams(toViewParams);
        }

        toolbar.processScrollCollapse(Toolbar.TOOLBAR_COLLAPSE_SHOW, animate);
    }

    public interface ToolbarSearchCallback {
        /**
         * Called when search visibility changes
         *
         * @param visible Visible or not
         */
        default void onSearchVisibilityChanged(boolean visible) {}

        /**
         * Called when search text is changed
         *
         * @param entered The search text
         */
        default void onSearchEntered(String entered) {}

        /**
         * Called whenever the navigation item for the toolbar is consistent with the controller being shown.
         */
        default void onNavItemSet() {}
    }
}
