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
import android.view.View;
import android.widget.FrameLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;

import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;

public class PopupController
        extends Controller
        implements View.OnClickListener {
    private FrameLayout container;

    public PopupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, R.layout.layout_controller_popup);
        FrameLayout topView = view.findViewById(R.id.top_view);
        topView.setOnClickListener(this);
        container = view.findViewById(R.id.container);
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
        if (presentedByController instanceof DoubleNavigationController) {
            ((SplitNavigationController) presentedByController).popAll();
        }
    }
}
