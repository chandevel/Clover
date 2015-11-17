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
package org.floens.chan.ui.controller;

import android.content.Context;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.ControllerTransition;
import org.floens.chan.controller.transition.PopControllerTransition;
import org.floens.chan.controller.transition.PushControllerTransition;
import org.floens.chan.utils.AndroidUtils;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class SplitNavigationController extends Controller implements AndroidUtils.OnMeasuredCallback {
    public Controller leftController;
    public Controller rightController;

    private FrameLayout leftControllerView;
    private FrameLayout rightControllerView;
    private View dividerView;
    private ViewGroup emptyView;

    private PopupController popup;
    private StyledToolbarNavigationController popupChild;

    public SplitNavigationController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        splitNavigationController = this;

        LinearLayout wrap = new LinearLayout(context);
        view = wrap;

        leftControllerView = new FrameLayout(context);
        wrap.addView(leftControllerView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT));

        dividerView = new View(context);
        dividerView.setBackgroundColor(getAttrColor(context, R.attr.divider_split_color));
        wrap.addView(dividerView, new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT));

        rightControllerView = new FrameLayout(context);
        wrap.addView(rightControllerView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        setRightController(null);

        AndroidUtils.waitForMeasure(view, this);
    }

    public void setEmptyView(ViewGroup emptyView) {
        this.emptyView = emptyView;
    }

    public void setLeftController(Controller leftController) {
        if (this.leftController != null) {
            this.leftController.onHide();
            removeChildController(this.leftController);
        }

        this.leftController = leftController;

        if (leftController != null) {
            addChildController(leftController);
            leftController.attachToParentView(leftControllerView, true);
            leftController.onShow();
        }
    }

    public void setRightController(Controller rightController) {
        if (this.rightController != null) {
            this.rightController.onHide();
            removeChildController(this.rightController);
        } else {
            rightControllerView.removeAllViews();
        }

        this.rightController = rightController;

        if (rightController != null) {
            addChildController(rightController);
            rightController.attachToParentView(rightControllerView, true);
            rightController.onShow();
        } else {
            rightControllerView.addView(emptyView);
        }
    }

    public boolean pushController(final Controller to) {
        return pushController(to, true);
    }

    public boolean pushController(final Controller to, boolean animated) {
        return pushController(to, animated ? new PushControllerTransition() : null);
    }

    public boolean pushController(Controller to, ControllerTransition controllerTransition) {
        if (popup == null) {
            popup = new PopupController(context);
            presentController(popup);
            popupChild = new StyledToolbarNavigationController(context);
            popup.setChildController(popupChild);
            popupChild.pushController(to, false);
        } else {
            popupChild.pushController(to, controllerTransition);
        }

        return true;
    }

    public boolean popController() {
        return popController(true);
    }

    public boolean popController(boolean animated) {
        return popController(animated ? new PopControllerTransition() : null);
    }

    public boolean popController(ControllerTransition controllerTransition) {
        if (popup != null) {
            if (popupChild.childControllers.size() == 1) {
                presentingThisController.stopPresenting();
                popup = null;
                popupChild = null;
            } else {
                popupChild.popController(controllerTransition);
            }
            return true;
        } else {
            return false;
        }
    }

    public void popAll() {
        if (popup != null) {
            presentingThisController.stopPresenting();
            popup = null;
            popupChild = null;
        }
    }

    @Override
    public boolean onBack() {
        if (leftController != null && leftController.onBack()) {
            return true;
        } else if (rightController != null && rightController.onBack()) {
            return true;
        }
        return super.onBack();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return (rightController != null && rightController.dispatchKeyEvent(event)) ||
                (leftController != null && leftController.dispatchKeyEvent(event)) ||
                super.dispatchKeyEvent(event);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        AndroidUtils.waitForMeasure(view, this);
    }

    @Override
    public boolean onMeasured(View view) {
        int width = Math.max(dp(300), (int) (view.getWidth() * 0.35));
        if (leftControllerView.getWidth() != width) {
            leftControllerView.getLayoutParams().width = width;
            leftControllerView.requestLayout();
            return true;
        } else {
            return false;
        }
    }
}
