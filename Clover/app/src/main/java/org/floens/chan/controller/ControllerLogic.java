package org.floens.chan.controller;

import android.view.ViewGroup;

import org.floens.chan.utils.AndroidUtils;

public class ControllerLogic {
    public static void attach(Controller controller, ViewGroup view, boolean over) {
        if (over) {
            view.addView(controller.view,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            );
        } else {
            view.addView(controller.view, 0,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            );
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
