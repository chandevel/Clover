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
package com.github.adamantcheese.chan.ui.toolbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;

/**
 * The container view for the list of ToolbarMenuItems, a list of ImageViews.
 */
public class ToolbarMenuView
        extends LinearLayout {
    private ToolbarMenu menu;

    public ToolbarMenuView(Context context) {
        this(context, null);
    }

    public ToolbarMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolbarMenuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    public void attach(ToolbarMenu menu) {
        this.menu = menu;

        setupMenuViews();
    }

    public void detach() {
        for (ToolbarMenuItem item : menu.items) {
            item.detach();
        }
    }

    private void setupMenuViews() {
        removeAllViews();

        for (ToolbarMenuItem item : menu.items) {
            ImageView imageView = new ImageView(getContext());

            imageView.setOnClickListener(v -> handleClick(item));
            imageView.setFocusable(true);
            imageView.setScaleType(ImageView.ScaleType.CENTER);

            imageView.setVisibility(item.visible ? VISIBLE : GONE);

            if (item.overflowStyle) {
                imageView.setLayoutParams(new LayoutParams(dp(44), dp(56)));
                imageView.setPadding(dp(8), 0, dp(16), 0);
            } else {
                imageView.setLayoutParams(new LinearLayout.LayoutParams(dp(50), dp(56)));
            }

            imageView.setImageDrawable(item.drawable);
            setRoundItemBackground(imageView);

            addView(imageView);

            item.attach(imageView);
        }
    }

    private void handleClick(ToolbarMenuItem item) {
        item.performClick();
    }
}
