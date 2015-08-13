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
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.ControllerLogic;
import org.floens.chan.controller.ControllerTransition;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.utils.AndroidUtils;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class SplitNavigationController extends NavigationController implements AndroidUtils.OnMeasuredCallback {
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

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (leftController != null) {
            leftController.onDestroy();
        }
        if (rightController != null) {
            rightController.onDestroy();
        }
    }

    public void setEmptyView(ViewGroup emptyView) {
        this.emptyView = emptyView;
    }

    public void setLeftController(Controller leftController) {
        leftController.navigationController = this;
        ControllerLogic.transition(this.leftController, leftController, true, true, leftControllerView);
        this.leftController = leftController;
    }

    public void setRightController(Controller rightController) {
        if (rightController != null) {
            rightController.navigationController = this;
        } else {
            rightControllerView.removeAllViews();
        }

        ControllerLogic.transition(this.rightController, rightController, true, true, rightControllerView);
        this.rightController = rightController;

        if (rightController == null) {
            rightControllerView.addView(emptyView);
        }
    }

    @Override
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

    @Override
    public boolean popController(ControllerTransition controllerTransition) {
        if (popup != null) {
            if (popupChild.getControllerList().size() == 1) {
                presentedController.stopPresenting();
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
            presentedController.stopPresenting();
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        AndroidUtils.waitForMeasure(view, this);
    }

    @Override
    public boolean onMeasured(View view) {
        int width = Math.max(dp(320), (int) (view.getWidth() * 0.35));
        if (leftControllerView.getWidth() != width) {
            leftControllerView.getLayoutParams().width = width;
            leftControllerView.requestLayout();
            return true;
        } else {
            return false;
        }
    }
}
