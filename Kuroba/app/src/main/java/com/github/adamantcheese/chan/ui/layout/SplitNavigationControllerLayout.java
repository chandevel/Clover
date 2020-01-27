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
package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

public class SplitNavigationControllerLayout
        extends LinearLayout {
    private final int dividerWidth;
    private final int minimumLeftWidth;
    private final double ratio;

    private ViewGroup leftView;
    private ViewGroup rightView;
    private View divider;

    public SplitNavigationControllerLayout(Context context) {
        this(context, null);
    }

    public SplitNavigationControllerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SplitNavigationControllerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setOrientation(LinearLayout.HORIZONTAL);

        dividerWidth = dp(1);
        minimumLeftWidth = dp(300);
        ratio = 0.35;
    }

    public void setLeftView(ViewGroup leftView) {
        this.leftView = leftView;
    }

    public void setRightView(ViewGroup rightView) {
        this.rightView = rightView;
    }

    public void setDivider(View divider) {
        this.divider = divider;
    }

    public void build() {
        addView(leftView, new LinearLayout.LayoutParams(0, MATCH_PARENT));
        addView(divider, new LinearLayout.LayoutParams(dividerWidth, MATCH_PARENT));
        addView(rightView, new LinearLayout.LayoutParams(0, MATCH_PARENT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        if (widthMode == MeasureSpec.UNSPECIFIED) {
            throw new IllegalArgumentException();
        }

        int minWidth = Math.min(minimumLeftWidth, widthSize / 2);
        int leftWidth = Math.max(minWidth, (int) (widthSize * ratio));
        int rightWidth = widthSize - dividerWidth - leftWidth;
        leftView.getLayoutParams().width = leftWidth;
        rightView.getLayoutParams().width = rightWidth;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
