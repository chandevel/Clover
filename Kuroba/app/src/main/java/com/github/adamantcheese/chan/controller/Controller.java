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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.removeFromParentView;

import android.content.Context;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;

import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.transition.FadeInTransition;
import com.github.adamantcheese.chan.controller.transition.FadeOutTransition;
import com.github.adamantcheese.chan.ui.controller.DoubleNavigationController;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import kotlin.jvm.functions.Function1;

public abstract class Controller {
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
        // for any controller, injection is taken care of here so the user can just specify the needed injections without
        // having to worry about this
        inject(this);
    }

    @CallSuper
    public void onCreate() {
        alive = true;
    }

    @CallSuper
    public void onShow() {
        shown = true;

        view.setVisibility(VISIBLE);

        for (Controller controller : childControllers) {
            if (!controller.shown) {
                controller.onShow();
            }
        }
    }

    @CallSuper
    public void onHide() {
        shown = false;

        view.setVisibility(GONE);

        for (Controller controller : childControllers) {
            if (controller.shown) {
                controller.onHide();
            }
        }
    }

    @CallSuper
    public void onDestroy() {
        alive = false;

        while (childControllers.size() > 0) {
            removeChildController(childControllers.get(0));
        }

        removeFromParentView(view);
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

    public void removeChildController(Controller controller) {
        controller.onDestroy();
        childControllers.remove(controller);
    }

    public void attachToParentView(ViewGroup parentView) {
        if (view.getParent() != null) {
            removeFromParentView(view);
        }

        if (parentView != null) {
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

    /**
     * @return true if the back press was handled by the controller, false to pass it on
     */
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

        if (animated) {
            ControllerTransition transition = new FadeInTransition();
            transition.to = controller;
            transition.setCallback(transition1 -> controller.onShow());
            transition.perform();
        } else {
            controller.onShow();
        }

        ((StartActivity) context).pushController(controller);
    }

    public boolean isAlreadyPresenting(Function1<Controller, Boolean> predicate) {
        return ((StartActivity) context).isControllerAdded(predicate);
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

        ((StartActivity) context).popController(this);
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
}
