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
package org.floens.chan.ui.view;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import org.floens.chan.ui.toolbar.Toolbar;

public class HidingFloatingActionButton extends FloatingActionButton implements Toolbar.ToolbarCollapseCallback {
    private boolean attachedToWindow;
    private Toolbar toolbar;
    private boolean attachedToToolbar;
    private CoordinatorLayout coordinatorLayout;
    private int currentCollapseTranslation;

    public HidingFloatingActionButton(Context context) {
        super(context);
    }

    public HidingFloatingActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HidingFloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setToolbar(Toolbar toolbar) {
        this.toolbar = toolbar;

        if (attachedToWindow && !attachedToToolbar) {
            toolbar.addCollapseCallback(this);
            attachedToToolbar = true;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;

        if (!(getParent() instanceof CoordinatorLayout)) {
            throw new IllegalArgumentException("HidingFloatingActionButton must be a parent of CoordinatorLayout");
        }

        coordinatorLayout = (CoordinatorLayout) getParent();

        if (toolbar != null && !attachedToToolbar) {
            toolbar.addCollapseCallback(this);
            attachedToToolbar = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        if (attachedToToolbar) {
            toolbar.removeCollapseCallback(this);
            attachedToToolbar = false;
        }
        coordinatorLayout = null;
    }

    @Override
    public void onCollapseTranslation(float offset) {
        if (isSnackbarShowing()) {
            currentCollapseTranslation = -1;
            return;
        }

//        Logger.test("onCollapseTranslation " + offset);

        int translation = (int) (getTotalHeight() * offset);
        if (translation != currentCollapseTranslation) {
            currentCollapseTranslation = translation;
            float diff = Math.abs(translation - getTranslationY());
            if (diff >= getHeight()) {
                animate().translationY(translation).setDuration(300).setStartDelay(0).setInterpolator(new DecelerateInterpolator(2f)).start();
            } else {
                setTranslationY(translation);
            }
        }
    }

    @Override
    public void onCollapseAnimation(boolean collapse) {
        if (isSnackbarShowing()) {
            currentCollapseTranslation = -1;
            return;
        }

//        Logger.test("onCollapseAnimation " + collapse);

        int translation = collapse ? getTotalHeight() : 0;
        if (translation != currentCollapseTranslation) {
            currentCollapseTranslation = translation;
            animate().translationY(translation).setDuration(300).setStartDelay(0).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    private int getTotalHeight() {
        return getHeight() + ((ViewGroup.MarginLayoutParams) getLayoutParams()).bottomMargin;
    }

    private boolean isSnackbarShowing() {
        for (int i = 0; i < coordinatorLayout.getChildCount(); i++) {
            if (coordinatorLayout.getChildAt(i) instanceof Snackbar.SnackbarLayout) {
                return true;
            }
        }

        return false;
    }
}
