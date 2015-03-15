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

public abstract class Controller {
    public Context context;
    public View view;

    public NavigationItem navigationItem = new NavigationItem();

    // NavigationControllers members
    public Controller previousSiblingController;
    public NavigationController navigationController;

    // Controller (for presenting) members
    public Controller presentingController;

    public Controller(Context context) {
        this.context = context;
    }

    public void onCreate() {
//        Logger.test(getClass().getSimpleName() + " onCreate");
    }

    public void onShow() {
//        Logger.test(getClass().getSimpleName() + " onShow");
    }

    public void onHide() {
//        Logger.test(getClass().getSimpleName() + " onHide");
    }

    public void onDestroy() {
//        Logger.test(getClass().getSimpleName() + " onDestroy");
    }

    public void onConfigurationChanged(Configuration newConfig) {
    }

    public boolean onBack() {
        return false;
    }

    public void presentController(Controller controller) {
        ViewGroup contentView = ((BoardActivity) context).getContentView();
        controller.presentingController = this;

        ControllerTransition transition = new FadeInTransition();
        transition.setCallback(new ControllerTransition.Callback() {
            @Override
            public void onControllerTransitionCompleted(ControllerTransition transition) {
                ControllerLogic.finishTransition(transition);
            }
        });
        ControllerLogic.startTransition(null, controller, false, true, contentView, transition);
        ((BoardActivity) context).addController(controller);
    }

    public void stopPresenting() {
        ViewGroup contentView = ((BoardActivity) context).getContentView();

        ControllerTransition transition = new FadeOutTransition();
        transition.setCallback(new ControllerTransition.Callback() {
            @Override
            public void onControllerTransitionCompleted(ControllerTransition transition) {
                ControllerLogic.finishTransition(transition);
            }
        });
        ControllerLogic.startTransition(this, null, true, false, contentView, transition);
        ((BoardActivity) context).removeController(Controller.this);
    }

    public View inflateRes(int resId) {
        return LayoutInflater.from(context).inflate(resId, null);
    }

    public String string(int id) {
        return context.getString(id);
    }
}
