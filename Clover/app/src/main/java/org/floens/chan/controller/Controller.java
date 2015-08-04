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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.floens.chan.ui.activity.BoardActivity;
import org.floens.chan.ui.toolbar.NavigationItem;
import org.floens.chan.utils.Logger;

public abstract class Controller {
    private static final boolean LOG_STATES = false;

    public Context context;
    public View view;

    public NavigationItem navigationItem = new NavigationItem();

    // NavigationControllers members
    public Controller previousSiblingController;
    public NavigationController navigationController;

    /**
     * Controller that this controller is presented by.
     */
    public Controller presentingController;

    /**
     * Controller that this controller is presenting.
     */
    public Controller presentedController;

    public boolean alive = false;

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
        if (LOG_STATES) {
            Logger.test(getClass().getSimpleName() + " onShow");
        }
    }

    public void onHide() {
        if (LOG_STATES) {
            Logger.test(getClass().getSimpleName() + " onHide");
        }
    }

    public void onDestroy() {
        alive = false;
        if (LOG_STATES) {
            Logger.test(getClass().getSimpleName() + " onDestroy");
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
    }

    public boolean onBack() {
        return false;
    }

    public void presentController(Controller controller) {
        presentController(controller, true);
    }

    public void presentController(Controller controller, boolean animated) {
        ViewGroup contentView = ((BoardActivity) context).getContentView();
        presentedController = controller;
        controller.presentingController = this;

        if (animated) {
            ControllerTransition transition = new FadeInTransition();
            transition.setCallback(new ControllerTransition.Callback() {
                @Override
                public void onControllerTransitionCompleted(ControllerTransition transition) {
                    ControllerLogic.finishTransition(transition);
                }
            });
            ControllerLogic.startTransition(null, controller, true, contentView, transition);
        } else {
            ControllerLogic.transition(null, controller, true, contentView);
        }
        ((BoardActivity) context).addController(controller);
    }

    public void stopPresenting() {
        stopPresenting(true);
    }

    public void stopPresenting(boolean animated) {
        ViewGroup contentView = ((BoardActivity) context).getContentView();

        if (animated) {
            ControllerTransition transition = new FadeOutTransition();
            transition.setCallback(new ControllerTransition.Callback() {
                @Override
                public void onControllerTransitionCompleted(ControllerTransition transition) {
                    ControllerLogic.finishTransition(transition);
                }
            });
            ControllerLogic.startTransition(this, null, false, contentView, transition);
        } else {
            ControllerLogic.transition(this, null, false, contentView);
        }
        ((BoardActivity) context).removeController(this);
        presentingController.presentedController = null;
    }

    public View inflateRes(int resId) {
        return LayoutInflater.from(context).inflate(resId, null);
    }

    public String string(int id) {
        return context.getString(id);
    }
}
