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
package org.floens.chan.ui.toolbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.floens.chan.R;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ToolbarMenu extends LinearLayout {
    private List<ToolbarMenuItem> items = new ArrayList<>();

    public ToolbarMenu(Context context) {
        this(context, null);
    }

    public ToolbarMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolbarMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
    }

    public ToolbarMenuItem addItem(ToolbarMenuItem item) {
        items.add(item);
        ImageView icon = item.getView();
        if (icon != null) {
            int viewIndex = Math.min(getChildCount(), item.getOrder());
            addView(icon, viewIndex);
        }
        return item;
    }

    public ToolbarMenuItem createOverflow(ToolbarMenuItem.ToolbarMenuItemCallback callback) {
        ToolbarMenuItem overflow = addItem(new ToolbarMenuItem(getContext(), callback, 100, 100, R.drawable.ic_more_vert_white_24dp));
        ImageView overflowImage = overflow.getView();
        overflowImage.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(54)));
        overflowImage.setPadding(dp(8), 0, dp(16), 0);

        return overflow;
    }
}
