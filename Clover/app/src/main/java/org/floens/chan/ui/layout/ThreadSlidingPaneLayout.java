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
package org.floens.chan.ui.layout;

import android.content.Context;
import android.os.Parcelable;
import android.support.v4.widget.SlidingPaneLayout;
import android.util.AttributeSet;
import android.view.ViewGroup;

import org.floens.chan.R;
import org.floens.chan.ui.controller.ThreadSlideController;
import org.floens.chan.utils.AndroidUtils;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ThreadSlidingPaneLayout extends SlidingPaneLayout {
    public ViewGroup leftPane;
    public ViewGroup rightPane;

    private ThreadSlideController threadSlideController;

    public ThreadSlidingPaneLayout(Context context) {
        this(context, null);
    }

    public ThreadSlidingPaneLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThreadSlidingPaneLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        leftPane = findViewById(R.id.left_pane);
        rightPane = findViewById(R.id.right_pane);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Forces a relayout after it has already been layed out, because SlidingPaneLayout sucks and otherwise
        // gives the children too much room until they request a relayout.
        AndroidUtils.waitForLayout(this, view -> {
            requestLayout();
            return false;
        });
    }

    public void setThreadSlideController(ThreadSlideController threadSlideController) {
        this.threadSlideController = threadSlideController;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
        if (threadSlideController != null) {
            threadSlideController.onSlidingPaneLayoutStateRestored();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        ViewGroup.LayoutParams leftParams = leftPane.getLayoutParams();
        ViewGroup.LayoutParams rightParams = rightPane.getLayoutParams();

        if (width < dp(500)) {
            leftParams.width = width - dp(30);
            rightParams.width = width;
        } else {
            leftParams.width = width - dp(60);
            rightParams.width = width;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
