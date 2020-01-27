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
package com.github.adamantcheese.chan.controller;

import android.content.Context;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.ViewGroup;

import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.transition.FadeInTransition;
import com.github.adamantcheese.chan.controller.transition.FadeOutTransition;
import com.github.adamantcheese.chan.ui.controller.DoubleNavigationController;
import com.github.adamantcheese.chan.ui.controller.ImageViewerNavigationController;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.removeFromParentView;

public abstract class Controller {
    private static final boolean LOG_STATES = false;

    public Context context;
    public ViewGroup view;

    public NavigationItem navigation = new NavigationItem();

    public Controller parentController;

    public List<Controller> childControllers = new ArrayList<>();

    // NavigationControllers members
    public Controller previousSiblingController;
    public NavigationController navigationController;

    public DoubleNavigationController doubleNavigationController;

    /**
     * Controller that this controller is presented by.
     */
    public Controller presentedByController;

    /**
     * Controller that this controller is presenting.
     */
    public Controller presentingThisController;

    public boolean alive = false;
    private boolean shown = false;

    public Controller(Context context) {
        this.context = context;
    }

    public void onCreate() {
        alive = true;
        if (LOG_STATES) {
            Logger.test(getClass().getSimpleName() + " onCreate");
        }
    }

    public void onShow() {
        shown = true;
        if (LOG_STATES) {
            Logger.test(getClass().getSimpleName() + " onShow");
        }

        view.setVisibility(VISIBLE);

        for (Controller controller : childControllers) {
            if (!controller.shown) {
                controller.onShow();
            }
        }
    }

    public void onHide() {
        shown = false;
        if (LOG_STATES) {
            Logger.test(getClass().getSimpleName() + " onHide");
        }

        view.setVisibility(GONE);

        for (Controller controller : childControllers) {
            if (controller.shown) {
                controller.onHide();
            }
        }
    }

    public void onDestroy() {
        alive = false;
        if (LOG_STATES) {
            Logger.test(getClass().getSimpleName() + " onDestroy");
        }

        while (childControllers.size() > 0) {
            removeChildController(childControllers.get(0));
        }

        if (removeFromParentView(view)) {
            if (LOG_STATES) {
                Logger.test(getClass().getSimpleName() + " view removed onDestroy");
            }
        }
    }

    public void addChildController(Controller controller) {
        childControllers.add(controller);
        controller.parentController = this;
        if (doubleNavigationController != null) {
            controller.doubleNavigationController = doubleNavigationController;
        }
        if (navigationController != null) {
            controller.navigationController = navigationController;
        }
        controller.onCreate();
    }

    public boolean removeChildController(Controller controller) {
        controller.onDestroy();
        return childControllers.remove(controller);
    }

    public void attachToParentView(ViewGroup parentView) {
        if (view.getParent() != null) {
            if (LOG_STATES) {
                Logger.test(getClass().getSimpleName() + " view removed");
            }
            removeFromParentView(view);
        }

        if (parentView != null) {
            if (LOG_STATES) {
                Logger.test(getClass().getSimpleName() + " view attached");
            }
            attachToView(parentView);
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        for (Controller controller : childControllers) {
            controller.onConfigurationChanged(newConfig);
        }
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        for (int i = childControllers.size() - 1; i >= 0; i--) {
            Controller controller = childControllers.get(i);
            if (controller.dispatchKeyEvent(event)) {
                return true;
            }
        }

        return false;
    }

    public boolean onBack() {
        for (int i = childControllers.size() - 1; i >= 0; i--) {
            Controller controller = childControllers.get(i);
            if (controller.onBack()) {
                return true;
            }
        }

        return false;
    }

    public void presentController(Controller controller) {
        presentController(controller, true);
    }

    public void presentController(Controller controller, boolean animated) {
        ViewGroup contentView = ((StartActivity) context).getContentView();
        presentingThisController = controller;
        controller.presentedByController = this;

        controller.onCreate();
        controller.attachToView(contentView);
        controller.onShow();

        if (animated) {
            ControllerTransition transition = new FadeInTransition();
            transition.to = controller;
            transition.perform();
        }

        ((StartActivity) context).addController(controller);
    }

    public boolean isAlreadyPresenting() {
        return ((StartActivity) context).isControllerAdded(c -> c instanceof ImageViewerNavigationController);
    }

    public void stopPresenting() {
        stopPresenting(true);
    }

    public void stopPresenting(boolean animated) {
        if (animated) {
            ControllerTransition transition = new FadeOutTransition();
            transition.from = this;
            transition.setCallback(transition1 -> finishPresenting());
            transition.perform();
        } else {
            finishPresenting();
        }

        ((StartActivity) context).removeController(this);
        presentedByController.presentingThisController = null;
    }

    private void finishPresenting() {
        onHide();
        onDestroy();
    }

    public Controller getTop() {
        if (childControllers.size() > 0) {
            return childControllers.get(childControllers.size() - 1);
        } else {
            return null;
        }
    }

    public Toolbar getToolbar() {
        return null;
    }

    private void attachToView(ViewGroup parentView) {
        ViewGroup.LayoutParams params = view.getLayoutParams();

        if (params == null) {
            params = new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        } else {
            params.width = MATCH_PARENT;
            params.height = MATCH_PARENT;
        }

        view.setLayoutParams(params);

        parentView.addView(view, view.getLayoutParams());
    }

    public interface ControllerPredicate {
        boolean test(Controller controller);
    }
}
