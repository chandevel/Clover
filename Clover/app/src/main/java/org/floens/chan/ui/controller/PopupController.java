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
import android.view.View;
import android.widget.FrameLayout;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.NavigationController;

public class PopupController extends Controller implements View.OnClickListener {
    private FrameLayout topView;
    private FrameLayout container;

    public PopupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.layout_controller_popup);
        topView = (FrameLayout) view.findViewById(R.id.top_view);
        topView.setOnClickListener(this);
        container = (FrameLayout) view.findViewById(R.id.container);
    }

    public void setChildController(NavigationController childController) {
        addChildController(childController);
        childController.attachToParentView(container);
        childController.onShow();
    }

    @Override
    public void onClick(View v) {
        dismiss();
    }

    public void dismiss() {
        if (presentingByController instanceof DoubleNavigationController) {
            ((SplitNavigationController) presentingByController).popAll();
        }
    }
}
