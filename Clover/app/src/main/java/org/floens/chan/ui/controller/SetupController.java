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
import android.view.animation.DecelerateInterpolator;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.transition.FadeInTransition;
import org.floens.chan.core.presenter.SetupPresenter;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.ui.toolbar.Toolbar;

public class SetupController extends ToolbarNavigationController implements SetupPresenter.Callback {
    private SetupPresenter presenter;

    public SetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_navigation_setup);
        container = view.findViewById(R.id.container);
        toolbar = view.findViewById(R.id.toolbar);
        toolbar.setArrowMenuIconShown(false);
        toolbar.setBackgroundColor(ThemeHelper.getInstance().getTheme().primaryColor.color);
        toolbar.setCallback(new Toolbar.SimpleToolbarCallback() {
            @Override
            public void onMenuOrBackClicked(boolean isArrow) {
            }
        });
        toolbar.setAlpha(0f);
        requireSpaceForToolbar = false;

        presenter = new SetupPresenter();
        presenter.create(this);
    }

    public SetupPresenter getPresenter() {
        return presenter;
    }

    @Override
    public void moveToIntro() {
        replaceController(new IntroController(context), false);
    }

    @Override
    public void moveToSiteSetup() {
        replaceController(new SitesSetupController(context), true);
    }

    private void replaceController(Controller to, boolean showToolbar) {
        if (blockingInput || toolbar.isTransitioning()) return;

        boolean animated = getTop() != null;

        transition(getTop(), to, true, animated ? new FadeInTransition() : null);

        toolbar.animate().alpha(showToolbar ? 1f : 0f)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
    }
}
