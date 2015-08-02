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

import android.view.ViewGroup;

import org.floens.chan.utils.AndroidUtils;

public class ControllerLogic {
    public static void attach(Controller controller, ViewGroup view, boolean over) {
        ViewGroup.LayoutParams params = controller.view.getLayoutParams();

        if (params == null) {
            params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        controller.view.setLayoutParams(params);

        if (over) {
            view.addView(controller.view, controller.view.getLayoutParams());
        } else {
            view.addView(controller.view, 0, controller.view.getLayoutParams());
        }
    }

    public static void detach(Controller controller) {
        AndroidUtils.removeFromParentView(controller.view);
    }

    public static void transition(Controller from, Controller to, boolean destroyFrom, boolean createTo, ViewGroup toView) {
        if (to != null) {
            if (createTo) {
                to.onCreate();
            }

            attach(to, toView, true);
            to.onShow();
        }

        if (from != null) {
            from.onHide();
            detach(from);

            if (destroyFrom) {
                from.onDestroy();
            }
        }
    }

    public static void startTransition(Controller from, Controller to, boolean destroyFrom, boolean createTo, ViewGroup toView, ControllerTransition transition) {
        transition.destroyFrom = destroyFrom;
        transition.from = from;
        transition.to = to;

        if (to != null) {
            if (createTo) {
                to.onCreate();
            }

            attach(to, toView, transition.viewOver);
            to.onShow();
        }

        transition.perform();
    }

    public static void finishTransition(ControllerTransition transition) {
        if (transition.from != null) {
            transition.from.onHide();
            detach(transition.from);

            if (transition.destroyFrom) {
                transition.from.onDestroy();
            }
        }
    }
}
