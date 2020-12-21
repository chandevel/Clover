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
package com.github.adamantcheese.chan.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

public class HidingFloatingActionButton
        extends FloatingActionButton
        implements Toolbar.ToolbarCollapseCallback {
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
        if (isSnackbarShowing()) return;

        int translation = (int) (getTotalHeight() * offset);
        if (translation != currentCollapseTranslation) {
            currentCollapseTranslation = translation;
            float diff = Math.abs(translation - getTranslationY());
            if (diff >= getHeight()) {
                Interpolator slowdown = new DecelerateInterpolator(2f);
                animate().translationY(translation).setInterpolator(slowdown).start();
            } else {
                setTranslationY(translation);
            }
        }
    }

    @Override
    public void onCollapseAnimation(boolean collapse) {
        if (isSnackbarShowing()) return;

        int translation = collapse ? getTotalHeight() : 0;
        if (translation != currentCollapseTranslation) {
            currentCollapseTranslation = translation;
            Interpolator slowdown = new DecelerateInterpolator(2f);
            animate().translationY(translation).setInterpolator(slowdown).start();
        }
    }

    private int getTotalHeight() {
        return getHeight() + ((ViewGroup.MarginLayoutParams) getLayoutParams()).bottomMargin;
    }

    private boolean isSnackbarShowing() {
        for (int i = 0; i < coordinatorLayout.getChildCount(); i++) {
            if (coordinatorLayout.getChildAt(i) instanceof Snackbar.SnackbarLayout) {
                currentCollapseTranslation = -1;
                return true;
            }
        }

        return false;
    }
}
