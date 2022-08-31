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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.adamantcheese.chan.R;

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
        updatePaddings(this, dp(6f), dp(3f), 0, 0);
    }

    public void attach(ToolbarMenu menu) {
        this.menu = menu;

        setupMenuViews();
    }

    public void detach() {
        if (menu != null) {
            for (ToolbarMenuItem item : menu.items) {
                item.detach();
            }
        }
    }

    private void setupMenuViews() {
        removeAllViews();

        for (ToolbarMenuItem item : menu.items) {
            ImageView imageView = new ImageView(getContext());

            imageView.setOnClickListener(item::performClick);
            imageView.setFocusable(true);
            imageView.setScaleType(ImageView.ScaleType.CENTER);

            imageView.setVisibility(item.visible ? VISIBLE : GONE);

            imageView.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
            updatePaddings(imageView, dp(10f), dp(10f), 0, 0);

            imageView.setImageDrawable(item.drawable);
            imageView.setBackgroundResource(R.drawable.ripple_item_background);

            addView(imageView);

            item.attach(imageView);
        }
    }
}
